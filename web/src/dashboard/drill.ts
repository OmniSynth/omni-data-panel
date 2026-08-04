/** 下钻栈中的一级过滤条件。 */
export interface DrillStackItem {
  field: string
  value: string
}

/**
 * 按已下钻层级过滤行（字段值字符串相等）。
 */
export function filterRowsByDrillStack(
  rows: Array<Record<string, unknown>>,
  stack: DrillStackItem[],
): Array<Record<string, unknown>> {
  if (!stack.length) return rows
  return rows.filter((row) =>
    stack.every((item) => String(row[item.field] ?? '') === item.value))
}

/**
 * 按类目列聚合数值列（SUM）；非有限数值不计入求和。
 */
export function aggregateRows(
  rows: Array<Record<string, unknown>>,
  category: string,
  valueCols: string[],
): { columns: string[]; rows: Array<Record<string, unknown>> } {
  const columns = [category, ...valueCols]
  const buckets = new Map<string, Record<string, unknown>>()
  for (const row of rows) {
    const key = String(row[category] ?? '')
    let bucket = buckets.get(key)
    if (!bucket) {
      bucket = { [category]: key }
      for (const col of valueCols) bucket[col] = 0
      buckets.set(key, bucket)
    }
    for (const col of valueCols) {
      const num = Number(row[col])
      if (Number.isFinite(num)) {
        bucket[col] = Number(bucket[col]) + num
      }
    }
  }
  return { columns, rows: [...buckets.values()] }
}

/** 是否配置了可下钻路径（至少两级维度）。 */
export function canDrill(drillPath: string[] | undefined): boolean {
  return Array.isArray(drillPath) && drillPath.length >= 2
}
