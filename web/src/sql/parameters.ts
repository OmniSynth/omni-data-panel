/** 粗略统计 SQL 中的 ? 占位符个数（忽略字符串字面量）。 */
export function countSqlPlaceholders(sql: string): number {
  let count = 0
  let inSingle = false
  let inDouble = false
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    if (ch === "'" && !inDouble) {
      if (inSingle && sql[i + 1] === "'") {
        i++
        continue
      }
      inSingle = !inSingle
      continue
    }
    if (ch === '"' && !inSingle) {
      inDouble = !inDouble
      continue
    }
    if (!inSingle && !inDouble && ch === '?') count++
  }
  return count
}

/** 将参数数组对齐到占位符个数。 */
export function alignSqlParameters(sql: string, parameters: unknown[]): unknown[] {
  const size = countSqlPlaceholders(sql)
  const next = parameters.slice(0, size)
  while (next.length < size) next.push('')
  return next
}
