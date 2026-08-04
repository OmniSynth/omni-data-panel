# -*- coding: utf-8 -*-
"""基于 sys_phone_area 创建手机号段归属地分析仪表盘。"""
import json
import urllib.request

BASE = "http://localhost:5173/api"
SOURCE_ID = "2084177715329564673"


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
    with urllib.request.urlopen(req) as resp:
        payload = json.load(resp)
    if payload.get("code") != 0:
        raise RuntimeError(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload["data"]


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


def create_sql_dataset(token, collection_id, name, description, sql):
    return api(
        token,
        "POST",
        "/datasets",
        {
            "name": name,
            "description": description,
            "modelType": "SQL",
            "dataSourceId": SOURCE_ID,
            "definitionSql": sql,
            "collectionId": collection_id,
            "fields": [
                {"name": "name", "columnName": "name", "fieldType": "DIMENSION"},
                {
                    "name": "cnt",
                    "columnName": "cnt",
                    "fieldType": "METRIC",
                    "aggregation": "SUM",
                },
            ],
        },
    )


def create_chart(token, collection_id, dataset_id, name, chart_type, category="name"):
    query_payload = {
        "query": {
            "datasetId": dataset_id,
            "dimensions": ["name"],
            "metrics": ["cnt"],
            "metricIds": None,
            "sorts": [{"field": "cnt", "direction": "DESC"}],
            "limit": 1000,
        }
    }
    config = {"encoding": {"category": category, "value": ["cnt"]}}
    return api(
        token,
        "POST",
        "/charts",
        {
            "name": name,
            "description": name,
            "datasetId": dataset_id,
            "queryJson": json.dumps(query_payload, ensure_ascii=False),
            "chartType": chart_type,
            "configJson": json.dumps(config, ensure_ascii=False),
            "collectionId": collection_id,
        },
    )


def main():
    token = login()
    collections = api(token, "GET", "/collections")
    collection_id = collections[0]["id"]
    print("collection=", collection_id)

    specs = [
        {
            "key": "province",
            "dataset_name": "手机号段-省分布",
            "chart_name": "省份分布",
            "chart_type": "pie",
            "sql": (
                "SELECT province AS name, COUNT(*) AS cnt "
                "FROM sys_phone_area "
                "WHERE province IS NOT NULL AND TRIM(province) <> '' "
                "GROUP BY province "
                "ORDER BY cnt DESC"
            ),
            "layout": {"x": 0, "y": 0, "w": 6, "h": 8},
        },
        {
            "key": "operator",
            "dataset_name": "手机号段-运营商分布",
            "chart_name": "运营商分布",
            "chart_type": "pie",
            "sql": (
                "SELECT operator AS name, COUNT(*) AS cnt "
                "FROM sys_phone_area "
                "WHERE operator IS NOT NULL AND TRIM(operator) <> '' "
                "GROUP BY operator "
                "ORDER BY cnt DESC"
            ),
            "layout": {"x": 6, "y": 0, "w": 6, "h": 8},
        },
        {
            "key": "city",
            "dataset_name": "手机号段-城市分布",
            "chart_name": "城市分布（Top 30）",
            "chart_type": "bar",
            "sql": (
                "SELECT city AS name, COUNT(*) AS cnt "
                "FROM sys_phone_area "
                "WHERE city IS NOT NULL AND TRIM(city) <> '' "
                "GROUP BY city "
                "ORDER BY cnt DESC "
                "LIMIT 30"
            ),
            "layout": {"x": 0, "y": 8, "w": 12, "h": 9},
        },
    ]

    charts = []
    for spec in specs:
        dataset = create_sql_dataset(
            token,
            collection_id,
            spec["dataset_name"],
            f"sys_phone_area {spec['key']} aggregation",
            spec["sql"],
        )
        dataset_id = dataset["id"]
        print("dataset", spec["key"], "=", dataset_id)
        chart = create_chart(
            token,
            collection_id,
            dataset_id,
            spec["chart_name"],
            spec["chart_type"],
        )
        print("chart", spec["key"], "=", chart["id"])
        charts.append((spec, chart))

    dashboard = api(
        token,
        "POST",
        "/dashboards",
        {
            "name": "手机号段归属地分析",
            "description": "基于 sys_phone_area：省份、运营商、城市号段数量分布",
            "configJson": json.dumps({"parameters": []}, ensure_ascii=False),
            "collectionId": collection_id,
        },
    )
    print("dashboard=", dashboard["id"])

    for spec, chart in charts:
        card = api(
            token,
            "POST",
            f"/dashboards/{dashboard['id']}/cards",
            {
                "chartId": chart["id"],
                "title": spec["chart_name"],
                "layoutJson": json.dumps(spec["layout"], ensure_ascii=False),
                "bindingsJson": "[]",
            },
        )
        print("card", spec["key"], "=", card["id"])

    render = api(
        token,
        "POST",
        f"/dashboards/{dashboard['id']}/render",
        {"forceRefresh": True, "parameterValues": {}},
    )
    for card in render.get("cards") or []:
        title = card.get("title")
        if card.get("error"):
            print("RENDER_ERROR", title, "=", card["error"])
            continue
        result = card.get("result") or {}
        rows = result.get("rows") or []
        cols = result.get("columns") or []
        print("RENDER_OK", title, "rows=", len(rows), "cols=", ",".join(cols))
        if rows:
            print("  sample=", rows[:3])

    print(f"VIEW=http://localhost:5173/dashboards/{dashboard['id']}/view")
    print(f"EDIT=http://localhost:5173/dashboards/{dashboard['id']}/edit")


if __name__ == "__main__":
    main()
