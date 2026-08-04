import type {
  CardClickAction,
  CardParameterBinding,
  DashboardConfig,
  DashboardParameter,
} from '@/types'
import { displayLabel } from '@/display'

export function parseDashboardConfig(configJson?: string): DashboardConfig {
  try {
    const parsed = JSON.parse(configJson || '{}') as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return { parameters: [] }
    const config = parsed as DashboardConfig
    return {
      parameters: Array.isArray(config.parameters) ? config.parameters : [],
    }
  } catch {
    return { parameters: [] }
  }
}

export function serializeDashboardConfig(config: DashboardConfig): string {
  return JSON.stringify({
    parameters: config.parameters || [],
  })
}

export function defaultParameterValues(parameters: DashboardParameter[]): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const parameter of parameters) {
    if (parameter.defaultValue !== undefined) values[parameter.id] = parameter.defaultValue
  }
  return values
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