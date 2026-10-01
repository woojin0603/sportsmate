"""Import current Sports Voucher facilities and courses through the protected API."""

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
from urllib.error import HTTPError, URLError


FACILITY_ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_OD_API_FACIL_MNG"
COURSE_ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_OD_API_FACIL_COURSE"
DATASET = "KSPO_SPORTS_VOUCHER"
WEEKDAYS = ("월", "화", "수", "목", "금", "토", "일")


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
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
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


def lookup_facility(key, course):
    items, _ = fetch(
        FACILITY_ENDPOINT,
        key,
        brno=course.get("brno"),
        facil_sn=course.get("facil_sn"),
        numOfRows=10,
    )
    target = facility_key(course)
    return next((item for item in items if facility_key(item) == target), None)


def import_row(base_url, import_key, course, facility):
    address = " ".join(filter(None, [clean(facility.get("road_addr"), 450), clean(facility.get("faci_daddr"), 100)]))
    region_name = " ".join(filter(None, [clean(facility.get("city_nm"), 40), clean(facility.get("local_nm"), 50)]))
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
        "fee": str(course.get("settl_amt")) if course.get("settl_amt") not in (None, "") else None,
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
    with urllib.request.urlopen(request, timeout=30) as response:
        response.read()
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--limit", type=int, default=240)
    parser.add_argument("--page-size", type=int, default=100)
    args = parser.parse_args()
    if args.limit < 1 or args.page_size < 1 or args.page_size > 100:
        parser.error("limit must be positive and page-size must be 1..100")
    import_key = os.environ.get("IMPORT_KEY", "").strip()
    if not import_key:
        parser.error("Set IMPORT_KEY to the same value as the server")
    key = api_key()
    imported = skipped = page = 0
    facility_cache = {}
    seen_courses = set()

    try:
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
                key_value = facility_key(course)
                if key_value not in facility_cache:
                    facility_cache[key_value] = lookup_facility(key, course)
                facility = facility_cache[key_value]
                if facility is None or not import_row(args.base_url, import_key, course, facility):
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

    print(f"Done: imported {imported}, skipped {skipped}, facilities {len(facility_cache)}")


if __name__ == "__main__":
    main()
