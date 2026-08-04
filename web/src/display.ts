import { t } from '@/i18n'

const DISPLAY_KEYS = [
  'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'ONLINE', 'OFFLINE',
  'bar', 'hbar', 'line', 'area', 'combo', 'pie', 'scatter', 'kpi', 'table', 'funnel', 'map',
  'QUEUED', 'READ', 'WRITE', 'OWNER', 'ADMIN',
  'EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'LIKE', 'IN',
  'ASC', 'DESC', 'SUM', 'AVG', 'COUNT', 'MAX', 'MIN',
  'DIMENSION', 'METRIC', 'TABLE', 'SQL',
  'MYSQL', 'MARIADB', 'POSTGRESQL', 'SQLITE', 'MSSQL', 'GENERIC',
] as const

const known = new Set<string>(DISPLAY_KEYS)

export function displayLabel(value?: string) {
  if (!value) return t('display.unknown')
  if (known.has(value)) return t(`display.${value}`)
  return value
}

const ISO_DATE_TIME =
  /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/i

/** 展示用时区：东八区（与产品面向区域一致） */
const DISPLAY_TIME_ZONE = 'Asia/Shanghai'

/**
 * 将后端时间格式化为 yyyy-MM-dd HH:mm:ss（东八区）。
 * - 带 Z/偏移的 Instant：按 Asia/Shanghai 换算
 * - 无时区的 LocalDateTime：按字面展示（与库内墙钟一致）
 */
export function formatDateTime(value?: string | number | Date | null): string {
  if (value == null || value === '') return t('common.emptyDash')
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? String(value) : formatParts(date)
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? t('common.emptyDash') : formatParts(value)
  }
  const text = String(value).trim()
  const matched = text.match(ISO_DATE_TIME)
  if (matched) {
    if (matched[3]) {
      const date = new Date(text)
      if (!Number.isNaN(date.getTime())) return formatParts(date)
    }
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
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '00'
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}:${get('second')}`
}
