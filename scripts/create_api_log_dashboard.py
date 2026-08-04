# -*- coding: utf-8 -*-
import json
import urllib.request

BASE = "http://localhost:5173/api"
TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsImlhdCI6MTc4NTgxMjk3MSwiZXhwIjoxNzg1ODQxNzcxfQ.zL4IEEXDEqHZEUAlWfmlE9AATW8fuyYNRqkneGa-sdI"
SOURCE_ID = "2084177715329564673"


def api(method: str, path: str, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(req) as resp:
        payload = json.load(resp)
    if payload.get("code") != 0:
        raise RuntimeError(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload["data"]


def main():
    login_req = urllib.request.Request(
        BASE + "/auth/login",
        data=b'{"username":"admin","password":"admin123"}',
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    login = json.load(urllib.request.urlopen(login_req))
    if login.get("code") != 0:
        raise RuntimeError(json.dumps(login, ensure_ascii=False))
    global TOKEN
    TOKEN = login["data"]["accessToken"]

    collections = api("GET", "/collections")
    collection_id = collections[0]["id"]
    print("collection=", collection_id)

    sql = (
        "SELECT DATE(create_time) AS stat_date, caller AS caller, "
        "COUNT(*) AS request_count FROM sys_api_log "
        "WHERE COALESCE(is_delete, 0) = 0 "
        "GROUP BY DATE(create_time), caller"
    )
    dataset = api(
        "POST",
        "/datasets",
        {
            "name": "外部接口请求日志(按日)",
            "description": "sys_api_log 按调用方与日期汇总请求次数",
            "modelType": "SQL",
            "dataSourceId": SOURCE_ID,
            "definitionSql": sql,
            "collectionId": collection_id,
            "fields": [
                {"name": "stat_date", "columnName": "stat_date", "fieldType": "DIMENSION"},
                {"name": "caller", "columnName": "caller", "fieldType": "DIMENSION"},
                {
                    "name": "request_count",
                    "columnName": "request_count",
                    "fieldType": "METRIC",
                    "aggregation": "SUM",
                },
            ],
        },
    )
    print("dataset=", dataset["id"])

    dataset_id = int(dataset["id"]) if str(dataset["id"]).isdigit() else dataset["id"]
    query_payload = {
        "query": {
            "datasetId": dataset_id,
            "dimensions": ["stat_date", "caller"],
            "metrics": ["request_count"],
            "metricIds": None,
            "sorts": [{"field": "stat_date", "direction": "ASC"}],
            "limit": 1000,
        }
    }
    config = {"encoding": {"category": "stat_date", "value": ["request_count"]}}
    chart_body = {
        "name": "调用方每日请求次数",
        "description": "按调用方与日期统计请求次数",
        "datasetId": dataset_id,
        "queryJson": json.dumps(query_payload, ensure_ascii=False),
        "chartType": "bar",
        "configJson": json.dumps(config, ensure_ascii=False),
        "collectionId": collection_id,
    }
    print("queryJson=", chart_body["queryJson"])
    chart = api("POST", "/charts", chart_body)
    print("chart=", chart["id"])

    from datetime import date, timedelta

    end = date.today()
    start = end - timedelta(days=7)
    dash_config = {
        "parameters": [
            {
                "id": "dateRange",
                "label": "日期范围",
                "type": "date-range",
                "required": True,
                "defaultValue": {"start": start.isoformat(), "end": end.isoformat()},
            },
            {
                "id": "caller",
                "label": "调用方标识",
                "type": "select",
                "required": False,
                "optionsFrom": {
                    "datasetId": dataset["id"],
                    "field": "caller",
                    "limit": 200,
                },
            },
        ]
    }
    dashboard = api(
        "POST",
        "/dashboards",
        {
            "name": "接口调用日报",
            "description": "大数据库 sys_api_log：按调用方查看每日请求次数，支持日期与调用方筛选",
            "configJson": json.dumps(dash_config, ensure_ascii=False),
            "collectionId": collection_id,
        },
    )
    print("dashboard=", dashboard["id"])

    bindings = [
        {"parameterId": "dateRange", "mode": "semantic", "field": "stat_date", "operator": "EQ"},
        {"parameterId": "caller", "mode": "semantic", "field": "caller", "operator": "EQ"},
    ]
    card = api(
        "POST",
        f"/dashboards/{dashboard['id']}/cards",
        {
            "chartId": chart["id"],
            "title": "调用方每日请求次数",
            "layoutJson": json.dumps({"x": 0, "y": 0, "w": 12, "h": 8}),
            "bindingsJson": json.dumps(bindings, ensure_ascii=False),
        },
    )
    print("card=", card["id"])

    render = api(
        "POST",
        f"/dashboards/{dashboard['id']}/render",
        {
            "forceRefresh": True,
            "parameterValues": {
                "dateRange": {"start": start.isoformat(), "end": end.isoformat()}
            },
        },
    )
    first = render["cards"][0]
    if first.get("error"):
        print("error=", first["error"])
    else:
        result = first.get("result") or {}
        print("rows=", len(result.get("rows") or []))
        print("cols=", ",".join(result.get("columns") or []))

    print(f"VIEW=http://localhost:5173/dashboards/{dashboard['id']}/view")
    print(f"EDIT=http://localhost:5173/dashboards/{dashboard['id']}/edit")


if __name__ == "__main__":
    main()
