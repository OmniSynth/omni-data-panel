# -*- coding: utf-8 -*-
import json
import urllib.error
import urllib.request

BASE = "http://localhost:5173/api"


def login():
    req = urllib.request.Request(
        BASE + "/auth/login",
        data=b'{"username":"admin","password":"admin123"}',
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    payload = json.load(urllib.request.urlopen(req))
    if payload.get("code") != 0:
        raise RuntimeError(json.dumps(payload, ensure_ascii=False))
    return payload["data"]["accessToken"]


def api(token: str, method: str, path: str, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            payload = json.load(resp)
    except urllib.error.HTTPError as err:
        raise RuntimeError(err.read().decode("utf-8", errors="replace")) from err
    if payload.get("code") != 0:
        raise RuntimeError(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload["data"]


def find_phone_table(token: str, source_id: str):
    schemas = api(token, "GET", f"/data-sources/{source_id}/metadata/schemas")
    hits = []
    for schema in schemas or []:
        tables = api(token, "GET", f"/data-sources/{source_id}/metadata/schemas/{schema}/tables")
        for table in tables or []:
            name = table.get("tableName") or table.get("name") or ""
            if name.lower() == "sys_phone_area":
                hits.append((schema, name))
    return hits


def main():
    token = login()
    sources = api(token, "GET", "/data-sources")
    print("sources:")
    for source in sources:
        print("-", source["id"], source.get("name"), source.get("dialect"), source.get("defaultDatabase"))
        try:
            hits = find_phone_table(token, str(source["id"]))
            print("  phone_table_hits=", hits)
        except Exception as exc:
            print("  meta_error=", str(exc)[:300])


if __name__ == "__main__":
    main()
