"""Collect one public API into normalized facility records.

Usage: python tools/collect.py config/facilities.example.json
The config's field map must be checked against the provider's actual response schema.
"""
import json
import os
import sys
import urllib.parse
import urllib.request


def pick(row, path):
    """Read a nested value from one public facility API response row."""
    value = row
    for part in path.split("."):
        if not isinstance(value, dict):
            return None
        value = value.get(part)
    return value


def collect(config):
    """Fetch configured facility pages and map source fields for import."""
    key = urllib.parse.unquote(os.environ[config["service_key_env"]])
    page = 1
    while True:
        params = dict(config.get("params", {}))
        params[config.get("key_param", "serviceKey")] = key
        params[config.get("page_param", "pageNo")] = page
        params[config.get("size_param", "numOfRows")] = config.get("page_size", 100)
        url = config["url"] + "?" + urllib.parse.urlencode(params)
        with urllib.request.urlopen(url, timeout=30) as response:
            payload = json.load(response)
        rows = pick(payload, config["items_path"])
        if isinstance(rows, dict):
            rows = [rows]
        if not rows:
            break
        for row in rows:
            fields = config["fields"]
            mapped = {target: pick(row, source) for target, source in fields.items()}
            if not mapped.get("sourceKey") or not mapped.get("name"):
                continue
            mapped["datasetCode"] = config["dataset_code"]
            mapped["rawJson"] = json.dumps(row, ensure_ascii=False)
            yield mapped
        if len(rows) < config.get("page_size", 100):
            break
        page += 1


def main():
    """Load collection settings and send the resulting records to Spring."""
    with open(sys.argv[1], encoding="utf-8") as file:
        config = json.load(file)
    import_key = os.environ["IMPORT_KEY"]
    import_url = os.environ.get("IMPORT_URL", "http://localhost:8080/api/import/facilities")
    count = 0
    for record in collect(config):
        body = json.dumps(record, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(import_url, data=body, headers={
            "Content-Type": "application/json", "X-Import-Key": import_key}, method="POST")
        with urllib.request.urlopen(request, timeout=30):
            count += 1
    print(f"Imported {count} records")


if __name__ == "__main__":
    main()
