const labels: Record<string, string> = {
  PENDING: '等待执行',
  RUNNING: '执行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  ONLINE: '在线',
  OFFLINE: '离线',
  bar: '柱状图',
  line: '折线图',
  pie: '饼图',
  table: '表格',
  QUEUED: '排队中',
  READ: '读取',
  WRITE: '写入',
  OWNER: '所有者',
  ADMIN: '管理员',
  EQ: '等于',
  NE: '不等于',
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于',
  LIKE: '包含',
  IN: '属于',
  ASC: '升序',
  DESC: '降序',
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  MAX: '最大',
  MIN: '最小',
  DIMENSION: '维度',
  METRIC: '指标',
  TABLE: '表',
  SQL: 'SQL',
  MYSQL: 'MySQL',
  MARIADB: 'MariaDB',
  POSTGRESQL: 'PostgreSQL',
  SQLITE: 'SQLite',
  MSSQL: 'SQL Server',
  GENERIC: '通用 SQL',
}

export function displayLabel(value?: string) {
  return value ? labels[value] || value : '未知'
}

const ISO_DATE_TIME =
  /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$/

/**
 * 将后端 ISO 时间（如 2026-08-03T17:48:46）格式化为 yyyy-MM-dd HH:mm:ss。
 */
export function formatDateTime(value?: string | number | Date | null): string {
  if (value == null || value === '') return '—'
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? String(value) : formatParts(date)
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? '—' : formatParts(value)
  }
  const text = String(value).trim()
  const matched = text.match(ISO_DATE_TIME)
  if (matched) {
    const time = matched[2].length === 5 ? `${matched[2]}:00` : matched[2]
    return `${matched[1]} ${time}`
  }
  const parsed = new Date(text)
  if (!Number.isNaN(parsed.getTime()) && /[T-]/.test(text)) {
    return formatParts(parsed)
  }
  return text
}

function formatParts(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
