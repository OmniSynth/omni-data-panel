import type { FilterCondition, QueryFilter } from '@/types'

/** 将可视化条件列表序列化为行级规则 JSON（AND 组合） */
export function conditionsToRuleJson(conditions: FilterCondition[]): string {
  const children = conditions
    .filter((item) => item.field?.trim())
    .map((item) => ({
      field: item.field.trim(),
      operator: item.operator,
      value: parseFilterValue(item.operator, item.value),
    }))
  if (!children.length) {
    throw new Error('EMPTY_FILTER')
  }
  if (children.length === 1) {
    return JSON.stringify(children[0])
  }
  return JSON.stringify({ logic: 'AND', children })
}

/** 将行级规则 JSON 解析为可视化条件列表 */
export function ruleJsonToConditions(ruleJson: string): FilterCondition[] {
  if (!ruleJson?.trim()) return []
  let node: QueryFilter
  try {
    node = JSON.parse(ruleJson) as QueryFilter
  } catch {
    return []
  }
  return flattenFilter(node)
}

function flattenFilter(node: QueryFilter | undefined): FilterCondition[] {
  if (!node) return []
  if (node.children?.length) {
    return node.children.flatMap((child) => flattenFilter(child))
  }
  if (!node.field || !node.operator) return []
  return [{
    field: node.field,
    operator: (node.operator as FilterCondition['operator']) || 'EQ',
    value: formatFilterValue(node.value),
  }]
}

function parseFilterValue(operator: string, raw: string): unknown {
  const text = raw?.trim() ?? ''
  if (operator === 'IN') {
    return text.split(',').map((part) => part.trim()).filter(Boolean)
  }
  if (text !== '' && !Number.isNaN(Number(text)) && /^-?\d+(\.\d+)?$/.test(text)) {
    return Number(text)
  }
  return text
}

function formatFilterValue(value: unknown): string {
  if (Array.isArray(value)) return value.map(String).join(',')
  if (value == null) return ''
  return String(value)
}
