# -*- coding: utf-8 -*-
import json
import urllib.error
import urllib.request

BASE = "http://localhost:5173/api"


def main():
    login_req = urllib.request.Request(
        BASE + "/auth/login",
        data=b'{"username":"admin","password":"admin123"}',
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    login = json.load(urllib.request.urlopen(login_req))
    print("login", login.get("code"))
    token = login["data"]["accessToken"]
    print("token_prefix", token[:20])

    for path in ["/collections/tree", "/collections", "/datasets", "/data-sources", "/auth/me"]:
        req = urllib.request.Request(
            BASE + path,
            headers={"Authorization": "Bearer " + token},
        )
        try:
            with urllib.request.urlopen(req) as resp:
                payload = json.load(resp)
            data = payload.get("data")
            if isinstance(data, list):
                print(path, payload.get("code"), "list", len(data))
            else:
                print(path, payload.get("code"), type(data).__name__)
        except urllib.error.HTTPError as err:
            body = err.read().decode("utf-8", errors="replace")
            print(path, "HTTP", err.code, body[:500])


if __name__ == "__main__":
    main()
