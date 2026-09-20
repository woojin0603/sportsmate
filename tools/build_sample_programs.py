"""Build a small, reproducible local preview from the supplied public program dataset."""

import argparse
from datetime import date
import json
from pathlib import Path

from import_programs import map_row, records


def main():
    """Select distinct current programs across regions and write import-ready JSON."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--limit", type=int, default=240)
    args = parser.parse_args()
    selected = []
    seen = set()
    per_region = {}
    today = date.today().isoformat().replace("-", "")

    for _, row in records(args.source):
        if str(row.get("PROGRM_END_DE", "")) < today:
            continue
        try:
            item = map_row(row)
        except (KeyError, ValueError, TypeError):
            continue
        region = item["regionName"] or "기타"
        if not item["name"] or not item["facilityName"] or item["sourceKey"] in seen:
            continue
        if per_region.get(region, 0) >= 12:
            continue
        selected.append(item)
        seen.add(item["sourceKey"])
        per_region[region] = per_region.get(region, 0) + 1
        if len(selected) >= args.limit:
            break

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(selected, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(selected)} programs from {len(per_region)} regions to {args.output}")


if __name__ == "__main__":
    main()
