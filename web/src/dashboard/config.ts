import type {
  CardClickAction,
  CardParameterBinding,
  DashboardConfig,
  DashboardLayout,
  DashboardParameter,
  DashboardTab,
} from '@/types'
import { displayLabel } from '@/display'

export function parseDashboardConfig(configJson?: string): DashboardConfig {
  try {
    const parsed = JSON.parse(configJson || '{}') as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { parameters: [], tabs: [] }
    }
    const config = parsed as DashboardConfig
    return {
      parameters: Array.isArray(config.parameters) ? config.parameters : [],
      tabs: normalizeTabs(config.tabs),
    }
  } catch {
    return { parameters: [], tabs: [] }
  }
}

export function serializeDashboardConfig(config: DashboardConfig): string {
  const tabs = normalizeTabs(config.tabs)
  const payload: DashboardConfig = {
    parameters: config.parameters || [],
  }
  if (tabs.length) payload.tabs = tabs
  return JSON.stringify(payload)
}

/** 规范化 tabs：过滤非法项并去重 id。 */
export function normalizeTabs(tabs?: DashboardTab[] | null): DashboardTab[] {
  if (!Array.isArray(tabs)) return []
  const seen = new Set<string>()
  const result: DashboardTab[] = []
  for (const item of tabs) {
    if (!item || typeof item !== 'object') continue
    const id = typeof item.id === 'string' ? item.id.trim() : ''
    const name = typeof item.name === 'string' ? item.name.trim() : ''
    if (!id || !name || seen.has(id)) continue
    seen.add(id)
    result.push({ id, name })
  }
  return result
}

export function createDashboardTab(name: string, existing: DashboardTab[] = []): DashboardTab {
  let index = existing.length + 1
  let id = `tab_${index}`
  while (existing.some((item) => item.id === id)) {
    index += 1
    id = `tab_${index}`
  }
  return { id, name: name.trim() || `Tab ${index}` }
}

export function parseLayoutJson(layoutJson?: string): DashboardLayout {
  const fallback: DashboardLayout = { x: 0, y: 0, w: 6, h: 4 }
  try {
    const parsed = JSON.parse(layoutJson || '{}') as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return fallback
    const layout = parsed as DashboardLayout
    if (![layout.x, layout.y, layout.w, layout.h].every(Number.isFinite)
      || layout.x < 0 || layout.y < 0 || layout.w <= 0 || layout.h <= 0) {
      return { ...fallback, tabId: typeof layout.tabId === 'string' ? layout.tabId : undefined }
    }
    return {
      x: Math.floor(layout.x),
      y: Math.floor(layout.y),
      w: Math.floor(layout.w),
      h: Math.floor(layout.h),
      tabId: typeof layout.tabId === 'string' && layout.tabId.trim() ? layout.tabId.trim() : undefined,
    }
  } catch {
    return fallback
  }
}

export function stringifyLayout(layout: DashboardLayout): string {
  const payload: DashboardLayout = {
    x: layout.x,
    y: layout.y,
    w: layout.w,
    h: layout.h,
  }
  if (layout.tabId) payload.tabId = layout.tabId
  return JSON.stringify(payload)
}

/** 卡片归属的 tabId；无 tabs 时返回 undefined；无匹配时归入首个 tab。 */
export function resolveCardTabId(layoutJson: string | undefined, tabs: DashboardTab[]): string | undefined {
  if (!tabs.length) return undefined
  const tabId = parseLayoutJson(layoutJson).tabId
  if (tabId && tabs.some((item) => item.id === tabId)) return tabId
  return tabs[0]?.id
}

export function filterCardsByTab<T extends { layoutJson: string }>(
  cards: T[],
  tabId: string | undefined,
  tabs: DashboardTab[],
): T[] {
  if (!tabs.length) return cards
  const active = tabId && tabs.some((item) => item.id === tabId) ? tabId : tabs[0]?.id
  return cards.filter((card) => resolveCardTabId(card.layoutJson, tabs) === active)
}

export function defaultParameterValues(parameters: DashboardParameter[]): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const parameter of parameters) {
    if (parameter.defaultValue === undefined) continue
    values[parameter.id] = resolveParameterDefault(parameter.type, parameter.defaultValue)
  }
  return values
}

/** 相对日期默认：当天 */
export const DATE_PRESET_TODAY = '$today'
/** 相对日期默认：近一周（含今天共 7 天） */
export const DATE_PRESET_LAST7DAYS = '$last7days'

export type DateDefaultPreset = typeof DATE_PRESET_TODAY | typeof DATE_PRESET_LAST7DAYS

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** 本地日历日 YYYY-MM-DD */
export function formatLocalDate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  next.setDate(next.getDate() + days)
  return next
}

/** 从 defaultValue 读取相对预设标记；固定值返回 null */
export function readDateDefaultPreset(defaultValue: unknown): DateDefaultPreset | null {
  if (defaultValue === DATE_PRESET_TODAY || defaultValue === DATE_PRESET_LAST7DAYS) {
    return defaultValue
  }
  if (defaultValue && typeof defaultValue === 'object' && !Array.isArray(defaultValue)) {
    const preset = (defaultValue as { preset?: unknown }).preset
    if (preset === DATE_PRESET_TODAY || preset === 'today') return DATE_PRESET_TODAY
    if (preset === DATE_PRESET_LAST7DAYS || preset === 'last7days') return DATE_PRESET_LAST7DAYS
  }
  return null
}

/**
 * 将参数默认值解析为运行时值；相对预设按本地日历展开。
 */
export function resolveParameterDefault(
  _type: DashboardParameter['type'] | string | undefined,
  defaultValue: unknown,
  now: Date = new Date(),
): unknown {
  const preset = readDateDefaultPreset(defaultValue)
  if (preset === DATE_PRESET_TODAY) {
    return formatLocalDate(now)
  }
  if (preset === DATE_PRESET_LAST7DAYS) {
    return {
      start: formatLocalDate(addDays(now, -6)),
      end: formatLocalDate(now),
    }
  }
  return defaultValue
}

export function parseBindings(bindingsJson?: string): CardParameterBinding[] {
  try {
    const parsed = JSON.parse(bindingsJson || '[]') as unknown
    return Array.isArray(parsed) ? parsed as CardParameterBinding[] : []
  } catch {
    return []
  }
}

export function serializeBindings(bindings: CardParameterBinding[]): string {
  return JSON.stringify(bindings)
}

export function parseClickAction(clickActionJson?: string | null): CardClickAction | null {
  if (!clickActionJson) return null
  try {
    const parsed = JSON.parse(clickActionJson) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    const action = parsed as CardClickAction
    if (!action.setParameterId) return null
    return {
      enabled: !!action.enabled,
      setParameterId: action.setParameterId,
      valueMode: action.valueMode === 'toggle' ? 'toggle' : 'replace',
    }
  } catch {
    return null
  }
}

export function serializeClickAction(action: CardClickAction | null): string | undefined {
  if (!action || !action.enabled || !action.setParameterId) return undefined
  return JSON.stringify(action)
}

export function applyClickToParameterValues(
  values: Record<string, unknown>,
  action: CardClickAction,
  clickedLabel: string,
  parameterType?: string,
): Record<string, unknown> {
  const next = { ...values }
  if (action.valueMode === 'toggle' || parameterType === 'multi-select') {
    const current = next[action.setParameterId]
    const list = Array.isArray(current)
      ? current.map(String)
      : current == null || current === ''
        ? []
        : [String(current)]
    const index = list.indexOf(clickedLabel)
    if (index >= 0) list.splice(index, 1)
    else list.push(clickedLabel)
    next[action.setParameterId] = list
    return next
  }
  next[action.setParameterId] = clickedLabel
  return next
}

export const CHART_TYPE_VALUES = [
  'table', 'bar', 'hbar', 'line', 'area', 'combo', 'pie', 'scatter', 'kpi', 'funnel', 'map',
] as const

export function chartTypeOptions() {
  return CHART_TYPE_VALUES.map((value) => ({
    value,
    label: displayLabel(value),
  }))
}

export interface ChartEncoding {
  category?: string
  value?: string | string[]
  seriesTypes?: Record<string, 'bar' | 'line'>
  /** 地图经度列 */
  lng?: string
  /** 地图纬度列 */
  lat?: string
}

export type ChartConfigObject = Record<string, unknown> & {
  encoding?: ChartEncoding
  /** 有序维度路径；长度 ≥ 2 时启用同结果集下钻 */
  drillPath?: string[]
}

export function parseChartConfig(configJson?: string): ChartConfigObject {
  try {
    const parsed = JSON.parse(configJson || '{}') as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const config = parsed as ChartConfigObject
    const drillPath = Array.isArray(config.drillPath)
      ? config.drillPath.filter((item): item is string => typeof item === 'string')
      : undefined
    return {
      ...config,
      drillPath: drillPath?.length ? drillPath : undefined,
    }
  } catch {
    return {}
  }
}

export function mergeChartConfig(
  base: Record<string, unknown>,
  encoding: ChartEncoding | undefined,
  drillPath?: string[],
): string {
  const next: ChartConfigObject = { ...base }
  const hasEncoding = !!(encoding && (
    encoding.category
    || encoding.value
    || encoding.lng
    || encoding.lat
    || (encoding.seriesTypes && Object.keys(encoding.seriesTypes).length)
  ))
  if (hasEncoding && encoding) {
    next.encoding = encoding
  } else {
    delete next.encoding
  }
  const path = (drillPath ?? (Array.isArray(next.drillPath) ? next.drillPath : undefined))
    ?.filter((item): item is string => typeof item === 'string' && !!item)
  if (path && path.length >= 2) {
    next.drillPath = path
  } else {
    delete next.drillPath
  }
  return JSON.stringify(next)
}

export function resolveValueColumns(encoding: ChartEncoding | undefined, columns: string[]): string[] {
  if (!encoding?.value) return []
  const values = Array.isArray(encoding.value) ? encoding.value : [encoding.value]
  return values.filter((column) => columns.includes(column))
}

/** 单数值类图表（pie / kpi / funnel / map 气泡大小） */
export function isSingleValueChart(chartType: string): boolean {
  return chartType === 'kpi' || chartType === 'pie' || chartType === 'scatter'
    || chartType === 'funnel' || chartType === 'map'
}