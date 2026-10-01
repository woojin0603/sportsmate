"""Import current Sports Voucher facilities and courses through the protected API."""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from urllib.error import HTTPError, URLError
from pathlib import Path


FACILITY_ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_OD_API_FACIL_MNG/todz_api_facil_mng_i"
COURSE_ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_OD_API_FACIL_COURSE/todz_api_facil_course_i"
DATASET = "KSPO_SPORTS_VOUCHER"
WEEKDAYS = ("월", "화", "수", "목", "금", "토", "일")
MAX_ATTEMPTS = 6


def api_key():
    value = os.environ.get("PUBLIC_FACILITY_SERVICE_KEY", "").strip()
    if not value:
        raise SystemExit("Set PUBLIC_FACILITY_SERVICE_KEY before running")
    return urllib.parse.unquote(value)


def fetch(endpoint, key, **parameters):
    query = {
        "serviceKey": key,
        "pageNo": parameters.pop("pageNo", 1),
        "numOfRows": parameters.pop("numOfRows", 100),
        "resultType": "JSON",
        **{name: value for name, value in parameters.items() if value not in (None, "")},
    }
    url = endpoint + "?" + urllib.parse.urlencode(query)
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    label = "facility API" if endpoint == FACILITY_ENDPOINT else "course API"
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
            break
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            if error.code < 500 and error.code != 429:
                raise RuntimeError(f"{label} HTTP {error.code}: {detail}") from error
            last_error = f"HTTP {error.code}: {detail}"
        except (URLError, ConnectionResetError, TimeoutError, OSError) as error:
            last_error = str(error)
        if attempt == MAX_ATTEMPTS:
            raise RuntimeError(
                f"{label} failed after {MAX_ATTEMPTS} attempts: {last_error}"
            )
        wait_seconds = min(2 ** (attempt - 1), 16)
        print(
            f"{label} request failed; retrying in {wait_seconds}s "
            f"({attempt}/{MAX_ATTEMPTS})",
            flush=True,
        )
        time.sleep(wait_seconds)
    header = payload.get("response", {}).get("header", {})
    if str(header.get("resultCode")) not in {"0", "00"}:
        raise RuntimeError("Public API error: " + str(header.get("resultCode")))
    body = payload.get("response", {}).get("body", {})
    items = body.get("items", {}).get("item", [])
    if isinstance(items, dict):
        items = [items]
    return items, int(body.get("totalCount", 0))


def facility_key(row):
    return f"{str(row.get('brno', '')).strip()}:{str(row.get('facil_sn', '')).strip()}"


def clean(value, limit):
    if value is None:
        return None
    text = " ".join(str(value).split()).strip()
    return text[:limit] or None


def schedule(row):
    bits = str(row.get("lectr_weekday_val", ""))
    days = [day for index, day in enumerate(WEEKDAYS) if index < len(bits) and bits[index] == "1"]
    period = " ~ ".join(filter(None, [clean(row.get("start_tm"), 20), clean(row.get("equip_tm"), 20)]))
    return " / ".join(filter(None, ["·".join(days), period])) or None


def load_facilities(key, cache_path, refresh=False):
    cache = Path(cache_path)
    if cache.exists() and not refresh:
        with cache.open("r", encoding="utf-8") as source:
            rows = json.load(source)
        print(f"Loaded {len(rows)} facilities from {cache}", flush=True)
    else:
        rows = []
        page = 0
        total = 1
        while page * 100 < total:
            page += 1
            items, total = fetch(
                FACILITY_ENDPOINT,
                key,
                pageNo=page,
                numOfRows=100,
            )
            rows.extend(items)
            if page % 25 == 0 or page * 100 >= total:
                print(f"Downloaded {len(rows)}/{total} facilities", flush=True)
            time.sleep(0.15)
        cache.parent.mkdir(parents=True, exist_ok=True)
        with cache.open("w", encoding="utf-8") as target:
            json.dump(rows, target, ensure_ascii=False)
        print(f"Saved facility cache to {cache}", flush=True)
    return {facility_key(row): row for row in rows if facility_key(row) != ":"}


def warm_up_server(base_url, timeout, attempts):
    url = base_url.rstrip("/") + "/api/health"
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.load(response)
            if payload.get("status") == "UP":
                print("MySportsMate server and database are ready", flush=True)
                return
            last_error = "health status is not UP"
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            last_error = f"HTTP {error.code}: {detail}"
        except (URLError, ConnectionResetError, TimeoutError, OSError) as error:
            last_error = str(error)
        if attempt == attempts:
            raise RuntimeError(
                f"server health check failed after {attempts} attempts: {last_error}"
            )
        wait_seconds = min(2 ** (attempt - 1), 16)
        print(
            f"Server is waking up; retrying in {wait_seconds}s "
            f"({attempt}/{attempts})",
            flush=True,
        )
        time.sleep(wait_seconds)


def import_row(base_url, import_key, course, facility, timeout, attempts):
    address = " ".join(
        filter(
            None,
            [
                clean(facility.get("road_addr"), 450),
                clean(facility.get("faci_daddr"), 100),
            ],
        )
    )
    region_name = " ".join(
        filter(
            None,
            [
                clean(facility.get("city_nm"), 40),
                clean(facility.get("local_nm"), 50),
            ],
        )
    )
    body = {
        "datasetCode": DATASET,
        "sourceKey": ("sv-course-" + str(course.get("course_no", "")))[:64],
        "facilitySourceKey": ("sv-facility-" + facility_key(facility))[:200],
        "facilityName": clean(facility.get("facil_nm"), 200),
        "address": clean(address, 500),
        "regionCode": clean(facility.get("local_cd"), 20),
        "regionName": clean(region_name, 100),
        "facilityType": clean(facility.get("main_event_nm"), 100),
        "phone": None,
        "latitude": None,
        "longitude": None,
        "name": clean(course.get("course_nm"), 200),
        "sportType": clean(course.get("item_nm"), 100),
        "scheduleText": clean(schedule(course), 500),
        "eligibility": clean(course.get("course_seta_desc_cn"), 500),
        "fee": (
            str(course.get("settl_amt"))
            if course.get("settl_amt") not in (None, "")
            else None
        ),
        "capacity": None,
        "beginsOn": None,
        "endsOn": None,
        "registrationUrl": None,
    }
    if not body["sourceKey"] or not body["facilityName"] or not body["name"]:
        return False
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/import/programs",
        data=data,
        headers={"Content-Type": "application/json", "X-Import-Key": import_key},
        method="POST",
    )
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                response.read()
            return True
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            if error.code < 500 and error.code != 429:
                raise RuntimeError(
                    f"MySportsMate import API HTTP {error.code}: {detail}"
                ) from error
            last_error = f"HTTP {error.code}: {detail}"
        except (URLError, ConnectionResetError, TimeoutError, OSError) as error:
            last_error = str(error)
        if attempt == attempts:
            raise RuntimeError(
                f"MySportsMate import API failed after {attempts} attempts: "
                f"{last_error}"
            )
        wait_seconds = min(2 ** (attempt - 1), 16)
        print(
            f"Import request timed out or failed; retrying in {wait_seconds}s "
            f"({attempt}/{attempts})",
            flush=True,
        )
        time.sleep(wait_seconds)
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--limit", type=int, default=240)
    parser.add_argument("--page-size", type=int, default=100)
    parser.add_argument(
        "--facility-cache",
        default="tools/.cache/sports-voucher-facilities.json",
    )
    parser.add_argument("--refresh-facilities", action="store_true")
    parser.add_argument("--import-timeout", type=int, default=90)
    parser.add_argument("--import-attempts", type=int, default=6)
    args = parser.parse_args()
    if (
        args.limit < 1
        or args.page_size < 1
        or args.page_size > 100
        or args.import_timeout < 1
        or args.import_attempts < 1
    ):
        parser.error(
            "limit, import-timeout and import-attempts must be positive; "
            "page-size must be 1..100"
        )
    import_key = os.environ.get("IMPORT_KEY", "").strip()
    if not import_key:
        parser.error("Set IMPORT_KEY to the same value as the server")
    key = api_key()
    imported = skipped = page = 0
    facilities = {}
    seen_courses = set()

    try:
        facilities = load_facilities(
            key,
            args.facility_cache,
            args.refresh_facilities,
        )
        warm_up_server(
            args.base_url,
            args.import_timeout,
            args.import_attempts,
        )
        while imported < args.limit:
            page += 1
            courses, total = fetch(
                COURSE_ENDPOINT,
                key,
                pageNo=page,
                numOfRows=args.page_size,
            )
            if not courses:
                break
            for course in courses:
                course_no = str(course.get("course_no", "")).strip()
                if not course_no or course_no in seen_courses:
                    skipped += 1
                    continue
                seen_courses.add(course_no)
                facility = facilities.get(facility_key(course))
                if facility is None or not import_row(
                    args.base_url,
                    import_key,
                    course,
                    facility,
                    args.import_timeout,
                    args.import_attempts,
                ):
                    skipped += 1
                    continue
                imported += 1
                if imported % 25 == 0:
                    print(f"Imported {imported}/{args.limit} programs", flush=True)
                if imported >= args.limit:
                    break
            if page * args.page_size >= total:
                break
    except (HTTPError, URLError, RuntimeError) as error:
        raise SystemExit(f"Sync stopped after {imported} imports: {error}") from error

    print(f"Done: imported {imported}, skipped {skipped}, facilities {len(facilities)}")


if __name__ == "__main__":
    main()
