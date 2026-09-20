"""Stream the public program JSON file into SportMap without loading 900 MB into memory."""
import argparse
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def digest(*parts):
    """Create a stable source identifier from the public row's key fields."""
    return hashlib.sha256("\x1f".join(str(part or "") for part in parts).encode("utf-8")).hexdigest()


def parse_date(value):
    """Convert the source dataset's YYYYMMDD date to ISO format."""
    return datetime.strptime(str(value), "%Y%m%d").date().isoformat()


def optional_text(value, length):
    """Normalize optional source text to the backend column length."""
    if value is None:
        return None
    return str(value).replace("<br>", " ").replace("<br/>", " ").strip()[:length] or None


def map_row(row):
    """Map one public program row to the authenticated import API payload."""
    facility_key = digest(row.get("CTPRVN_CD"), row.get("SIGNGU_CD"),
                          row.get("FCLTY_NM"), row.get("FCLTY_ADDR"))
    source_key = digest(facility_key, row.get("PROGRM_NM"), row.get("PROGRM_BEGIN_DE"),
                        row.get("PROGRM_END_DE"), row.get("PROGRM_ESTBL_WKDAY_NM"),
                        row.get("PROGRM_ESTBL_TIZN_VALUE"), row.get("PROGRM_TRGET_NM"),
                        row.get("PROGRM_TY_NM"), row.get("PROGRM_PRC"),
                        row.get("PROGRM_RCRIT_NMPR_CO"))
    capacity = row.get("PROGRM_RCRIT_NMPR_CO")
    fee = row.get("PROGRM_PRC")
    schedule = " / ".join(filter(None, [optional_text(row.get("PROGRM_ESTBL_WKDAY_NM"), 200),
                                          optional_text(row.get("PROGRM_ESTBL_TIZN_VALUE"), 200)]))
    return {
        "sourceKey": source_key,
        "facilitySourceKey": facility_key,
        "facilityName": optional_text(row.get("FCLTY_NM"), 200),
        "address": optional_text(row.get("FCLTY_ADDR"), 500),
        "regionCode": optional_text(row.get("SIGNGU_CD"), 20),
        "regionName": optional_text(row.get("SIGNGU_NM"), 100),
        "facilityType": optional_text(row.get("FCLTY_TY_NM"), 100),
        "phone": optional_text(row.get("FCLTY_TEL_NO"), 50),
        "latitude": str(Decimal(str(row["FCLTY_LA"]))) if row.get("FCLTY_LA") not in (None, "") else None,
        "longitude": str(Decimal(str(row["FCLTY_LO"]))) if row.get("FCLTY_LO") not in (None, "") else None,
        "name": optional_text(row.get("PROGRM_NM"), 200),
        "sportType": optional_text(row.get("PROGRM_TY_NM"), 100),
        "scheduleText": schedule or None,
        "eligibility": optional_text(row.get("PROGRM_TRGET_NM"), 500),
        "fee": str(Decimal(str(fee))) if fee not in (None, "") else None,
        "capacity": int(capacity) if capacity not in (None, "") else None,
        "beginsOn": parse_date(row["PROGRM_BEGIN_DE"]),
        "endsOn": parse_date(row["PROGRM_END_DE"]),
        "registrationUrl": optional_text(row.get("HMPG_URL"), 500),
    }


def records(path):
    """Stream newline-separated JSON records without loading the full file."""
    with path.open(encoding="utf-8-sig") as stream:
        for number, line in enumerate(stream, 1):
            line = line.strip().lstrip(",").removeprefix("[").removesuffix("]")
            if line:
                yield number, json.loads(line)


def main():
    """Import current, distinct programs into the running Spring application."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--limit", type=int, default=None, help="Maximum rows to import")
    parser.add_argument("--include-expired", action="store_true")
    args = parser.parse_args()
    key = os.environ.get("IMPORT_KEY", "")
    if not key:
        parser.error("Set IMPORT_KEY to the same value as the server")
    imported = skipped = duplicates = 0
    seen = set()
    endpoint = args.base_url.rstrip("/") + "/api/import/programs"
    for number, row in records(args.file):
        if not args.include_expired and parse_date(row["PROGRM_END_DE"]) < date.today().isoformat():
            skipped += 1
            continue
        try:
            body = map_row(row)
        except (ValueError, KeyError, InvalidOperation) as exc:
            print(f"Skipping invalid row {number}: {exc}", file=sys.stderr)
            skipped += 1
            continue
        if body["sourceKey"] in seen:
            duplicates += 1
            continue
        seen.add(body["sourceKey"])
        request = Request(endpoint, json.dumps(body, ensure_ascii=False).encode("utf-8"),
                          {"Content-Type": "application/json", "X-Import-Key": key}, method="POST")
        try:
            with urlopen(request, timeout=30) as response:
                response.read()
        except (HTTPError, URLError) as exc:
            raise SystemExit(f"Import stopped at row {number}: {exc}") from exc
        imported += 1
        if imported % 500 == 0:
            print(f"Imported {imported}, expired/invalid {skipped}, duplicate rows {duplicates}", flush=True)
        if args.limit is not None and imported >= args.limit:
            break
    print(f"Done: imported {imported}, expired/invalid {skipped}, duplicate rows {duplicates}")


if __name__ == "__main__":
    main()
