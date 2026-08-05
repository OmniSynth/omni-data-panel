/**
 * 基于「大数据库」.sys_api_log 创建模型 / 指标 / 图表 / 带日期参数的仪表盘。
 * 用法：node scripts/setup-api-log-dashboard.mjs
 */
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const API = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const USER = process.env.E2E_USERNAME || 'admin'
const PASS = process.env.E2E_PASSWORD || 'admin123'
const SOURCE_NAME = '大数据库'
const SCHEMA = 'big_data'
const TABLE = 'sys_api_log'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(__dirname, '..', '..', 'docs', 'assets', 'user-guide')

async function api(token, method, url, body) {
  const res = await fetch(`${API}${url}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await res.json()
  if (json.code !== 0) {
    throw new Error(`${method} ${url} => ${json.code} ${json.message}`)
  }
  return json.data
}

async function login() {
  const ch = await fetch(`${API}/auth/login-challenge`).then((r) => r.json())
  const d = ch.data
  const timestamp = Math.floor(Date.now() / 1000)
  const payload = `${USER}\n${PASS}\n${d.nonce}\n${timestamp}`
  const signature = crypto.createHmac('sha256', Buffer.from(d.signKey, 'hex')).update(payload).digest('hex')
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: USER,
      password: PASS,
      challengeId: d.challengeId,
      nonce: d.nonce,
      timestamp,
      signature,
    }),
  }).then((r) => r.json())
  if (login.code !== 0 || !login.data?.accessToken) {
    throw new Error(`登录失败: ${JSON.stringify(login)}`)
  }
  return login.data.accessToken
}

function fieldDefs() {
  return [
    { name: 'ID', columnName: 'id', fieldType: 'METRIC', aggregation: 'COUNT' },
    { name: '调用方', columnName: 'caller', fieldType: 'DIMENSION', aggregation: null },
    { name: '请求URL', columnName: 'request_url', fieldType: 'DIMENSION', aggregation: null },
    { name: '请求方式', columnName: 'request_method', fieldType: 'DIMENSION', aggregation: null },
    { name: 'HTTP状态码', columnName: 'response_code', fieldType: 'DIMENSION', aggregation: null },
    { name: '是否成功', columnName: 'success', fieldType: 'DIMENSION', aggregation: null },
    { name: '耗时毫秒', columnName: 'cost_time', fieldType: 'METRIC', aggregation: 'AVG' },
    { name: '创建时间', columnName: 'create_time', fieldType: 'DIMENSION', aggregation: null },
    { name: '异常信息', columnName: 'error_msg', fieldType: 'DIMENSION', aggregation: null },
  ]
}

function visualQuery(datasetId, { dimensions = [], metrics = [], metricIds = [], filter, limit = 100 } = {}) {
  return JSON.stringify({
    query: {
      datasetId: String(datasetId),
      dimensions,
      metrics,
      metricIds: metricIds.map(String),
      filter,
      sorts: [],
      limit,
    },
  })
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const token = await login()
  console.log('已登录')

  const sources = await api(token, 'GET', '/data-sources')
  const source = sources.find((s) => s.name === SOURCE_NAME)
  if (!source) throw new Error(`未找到数据源「${SOURCE_NAME}」`)
  const sourceId = String(source.id)
  console.log('数据源', sourceId, source.jdbcUrl)

  try {
    await api(token, 'POST', `/data-sources/${sourceId}/metadata/sync`)
    console.log('元数据已同步')
  } catch (e) {
    console.warn('同步元数据失败（继续）', e.message)
  }

  // 模型（雪花 ID 必须用字符串，禁止 Number()）
  let datasets = await api(token, 'GET', '/datasets')
  let dataset = datasets.find((d) => d.name === '外部接口请求日志' && String(d.dataSourceId) === sourceId)
  const fields = fieldDefs()
  const datasetBody = {
    name: '外部接口请求日志',
    description: 'big_data.sys_api_log 外部接口请求日志',
    modelType: 'TABLE',
    dataSourceId: sourceId,
    schemaName: SCHEMA,
    tableName: TABLE,
    fields,
  }
  if (dataset) {
    dataset = await api(token, 'PUT', `/datasets/${dataset.id}`, datasetBody)
    console.log('已更新模型', dataset.id)
  } else {
    dataset = await api(token, 'POST', '/datasets', datasetBody)
    console.log('已创建模型', dataset.id)
  }
  const datasetId = String(dataset.id)

  // 指标
  let metrics = await api(token, 'GET', '/metrics')
  async function upsertMetric(name, field, aggregation) {
    let m = metrics.find((x) => x.name === name && String(x.modelId || x.datasetId) === datasetId)
    const body = {
      name,
      description: `${name}（${TABLE}.${field}）`,
      modelId: datasetId,
      expressionJson: JSON.stringify({ field }),
      aggregation,
    }
    if (m) {
      m = await api(token, 'PUT', `/metrics/${m.id}`, body)
    } else {
      m = await api(token, 'POST', '/metrics', body)
    }
    return m
  }
  const metricCount = await upsertMetric('接口请求次数', 'ID', 'COUNT')
  const metricAvgCost = await upsertMetric('接口平均耗时', '耗时毫秒', 'AVG')
  console.log('指标', metricCount.id, metricAvgCost.id)

  // 图表
  const chartsSpec = [
    {
      key: 'kpi-count',
      name: '接口请求总量',
      chartType: 'kpi',
      queryJson: visualQuery(datasetId, {
        metrics: ['ID'],
        metricIds: [metricCount.id],
        limit: 1,
      }),
      configJson: JSON.stringify({ encoding: { value: '接口请求次数' } }),
    },
    {
      key: 'kpi-avg',
      name: '接口平均耗时ms',
      chartType: 'kpi',
      queryJson: visualQuery(datasetId, {
        metrics: ['耗时毫秒'],
        metricIds: [metricAvgCost.id],
        limit: 1,
      }),
      configJson: JSON.stringify({ encoding: { value: '接口平均耗时' } }),
    },
    {
      key: 'bar-caller',
      name: '按调用方请求量',
      chartType: 'bar',
      queryJson: visualQuery(datasetId, {
        dimensions: ['调用方'],
        metrics: ['ID'],
        metricIds: [metricCount.id],
        limit: 20,
      }),
      configJson: JSON.stringify({ encoding: { category: '调用方', value: '接口请求次数' } }),
    },
    {
      key: 'bar-method',
      name: '按请求方式分布',
      chartType: 'pie',
      queryJson: visualQuery(datasetId, {
        dimensions: ['请求方式'],
        metrics: ['ID'],
        metricIds: [metricCount.id],
        limit: 20,
      }),
      configJson: JSON.stringify({ encoding: { category: '请求方式', value: '接口请求次数' } }),
    },
    {
      key: 'line-daily',
      name: '每日请求趋势',
      chartType: 'line',
      dataSourceId: sourceId,
      queryJson: JSON.stringify({
        sourceId: sourceId,
        sql: `SELECT DATE(create_time) AS day,
       COUNT(*) AS request_count,
       ROUND(AVG(cost_time), 2) AS avg_cost_ms
FROM \`${SCHEMA}\`.\`${TABLE}\`
WHERE create_time >= ? AND create_time < DATE_ADD(?, INTERVAL 1 DAY)
  AND (is_delete = 0 OR is_delete IS NULL)
GROUP BY DATE(create_time)
ORDER BY day`,
        parameters: ['2020-01-01', '2099-12-31'],
      }),
      configJson: JSON.stringify({ encoding: { category: 'day', value: 'request_count' } }),
    },
    {
      key: 'table-recent',
      name: '最近请求明细',
      chartType: 'table',
      queryJson: visualQuery(datasetId, {
        dimensions: ['创建时间', '调用方', '请求方式', '请求URL', 'HTTP状态码', '是否成功', '耗时毫秒'],
        limit: 50,
      }),
      configJson: '{}',
    },
  ]

  let charts = await api(token, 'GET', '/charts')
  const chartIds = {}
  for (const spec of chartsSpec) {
    let chart = charts.find((c) => c.name === spec.name)
    const body = {
      name: spec.name,
      description: `sys_api_log · ${spec.key}`,
      datasetId: spec.dataSourceId ? undefined : datasetId,
      dataSourceId: spec.dataSourceId,
      queryJson: spec.queryJson,
      chartType: spec.chartType,
      configJson: spec.configJson,
    }
    if (chart) {
      chart = await api(token, 'PUT', `/charts/${chart.id}`, body)
    } else {
      chart = await api(token, 'POST', '/charts', body)
    }
    chartIds[spec.key] = String(chart.id)
    console.log('图表', spec.name, chart.id)
  }

  // 仪表盘
  const dashName = '接口请求日志看板'
  let dashes = await api(token, 'GET', '/dashboards')
  let dash = dashes.find((d) => d.name === dashName)
  const config = {
    parameters: [
      {
        id: 'date_range',
        label: '请求日期',
        type: 'date-range',
        required: false,
        defaultValue: null,
      },
      {
        id: 'caller',
        label: '调用方',
        type: 'select',
        required: false,
        optionsFrom: {
          datasetId: Number(datasetId),
          field: '调用方',
          limit: 200,
        },
      },
      {
        id: 'success',
        label: '是否成功',
        type: 'select',
        required: false,
        options: [
          { label: '成功', value: '1' },
          { label: '失败', value: '0' },
        ],
      },
    ],
    tabs: [],
  }
  if (dash) {
    dash = await api(token, 'PUT', `/dashboards/${dash.id}`, {
      name: dashName,
      description: 'sys_api_log：日期区间 / 调用方 / 成功状态筛选',
      configJson: JSON.stringify(config),
    })
  } else {
    dash = await api(token, 'POST', '/dashboards', {
      name: dashName,
      description: 'sys_api_log：日期区间 / 调用方 / 成功状态筛选',
      configJson: JSON.stringify(config),
    })
  }
  const dashId = String(dash.id)
  console.log('仪表盘', dashId)

  // 重建卡片
  const existingCards = await api(token, 'GET', `/dashboards/${dashId}/cards`)
  for (const card of existingCards) {
    await api(token, 'DELETE', `/dashboards/${dashId}/cards/${card.id}`)
  }

  const semanticBindings = () =>
    JSON.stringify([
      { parameterId: 'date_range', mode: 'semantic', field: '创建时间' },
      { parameterId: 'caller', mode: 'semantic', field: '调用方', operator: 'EQ' },
      { parameterId: 'success', mode: 'semantic', field: '是否成功', operator: 'EQ' },
    ])

  const cards = [
    {
      chartId: chartIds['kpi-count'],
      title: '请求总量',
      layoutJson: JSON.stringify({ x: 0, y: 0, w: 3, h: 2 }),
      bindingsJson: semanticBindings(),
    },
    {
      chartId: chartIds['kpi-avg'],
      title: '平均耗时(ms)',
      layoutJson: JSON.stringify({ x: 3, y: 0, w: 3, h: 2 }),
      bindingsJson: semanticBindings(),
    },
    {
      chartId: chartIds['bar-caller'],
      title: '按调用方',
      layoutJson: JSON.stringify({ x: 6, y: 0, w: 6, h: 4 }),
      bindingsJson: semanticBindings(),
      clickActionJson: JSON.stringify({
        enabled: true,
        parameterId: 'caller',
        writeMode: 'replace',
      }),
    },
    {
      chartId: chartIds['bar-method'],
      title: '请求方式',
      layoutJson: JSON.stringify({ x: 0, y: 2, w: 6, h: 4 }),
      bindingsJson: semanticBindings(),
    },
    {
      chartId: chartIds['line-daily'],
      title: '每日趋势',
      layoutJson: JSON.stringify({ x: 0, y: 6, w: 12, h: 4 }),
      bindingsJson: '[]',
    },
    {
      chartId: chartIds['table-recent'],
      title: '最近明细',
      layoutJson: JSON.stringify({ x: 0, y: 10, w: 12, h: 6 }),
      bindingsJson: semanticBindings(),
    },
  ]

  for (const card of cards) {
    await api(token, 'POST', `/dashboards/${dashId}/cards`, card)
  }
  console.log('卡片已创建', cards.length)

  // 每日趋势：start_date / end_date 两个标量绑 SQL ?
  config.parameters = [
    {
      id: 'date_range',
      label: '请求日期',
      type: 'date-range',
      required: false,
    },
    {
      id: 'start_date',
      label: '趋势开始',
      type: 'date',
      required: false,
      defaultValue: '2024-01-01',
    },
    {
      id: 'end_date',
      label: '趋势结束',
      type: 'date',
      required: false,
      defaultValue: '2026-12-31',
    },
    {
      id: 'caller',
      label: '调用方',
      type: 'select',
      required: false,
      optionsFrom: {
        datasetId: datasetId,
        field: '调用方',
        limit: 200,
      },
    },
    {
      id: 'success',
      label: '是否成功',
      type: 'select',
      required: false,
      options: [
        { label: '成功', value: '1' },
        { label: '失败', value: '0' },
      ],
    },
  ]
  await api(token, 'PUT', `/dashboards/${dashId}`, {
    name: dashName,
    description: 'sys_api_log：日期区间 / 调用方 / 成功状态筛选',
    configJson: JSON.stringify(config),
  })

  const cardsAfter = await api(token, 'GET', `/dashboards/${dashId}/cards`)
  const trendCard = cardsAfter.find((c) => c.title === '每日趋势')
  if (trendCard) {
    await api(token, 'PUT', `/dashboards/${dashId}/cards/${trendCard.id}`, {
      chartId: chartIds['line-daily'],
      title: '每日趋势',
      layoutJson: trendCard.layoutJson,
      bindingsJson: JSON.stringify([
        { parameterId: 'start_date', mode: 'sql', parameterIndex: 0 },
        { parameterId: 'end_date', mode: 'sql', parameterIndex: 1 },
      ]),
    })
  }

  const summary = {
    sourceId,
    datasetId,
    dashId,
    chartIds,
    metricIds: { count: String(metricCount.id), avgCost: String(metricAvgCost.id) },
    viewUrl: `http://localhost:5173/dashboards/${dashId}/view`,
    editUrl: `http://localhost:5173/dashboards/${dashId}/edit`,
  }
  fs.writeFileSync(path.join(OUT, 'setup-result.json'), JSON.stringify(summary, null, 2))
  console.log(JSON.stringify(summary, null, 2))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
