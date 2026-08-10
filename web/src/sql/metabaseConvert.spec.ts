import { describe, expect, it } from 'vitest'
import { convertMetabaseSql } from '@/sql/metabaseConvert'

describe('convertMetabaseSql', () => {
  it('无 Metabase 语法时 changed 为 false', () => {
    const sql = 'SELECT * FROM t WHERE id = :id'
    const result = convertMetabaseSql(sql)
    expect(result.changed).toBe(false)
    expect(result.sql).toBe(sql)
    expect(result.warnings).toEqual([])
  })

  it('将 {{var}} 转为 :var', () => {
    const result = convertMetabaseSql('SELECT * FROM t WHERE id = {{user_id}}')
    expect(result.changed).toBe(true)
    expect(result.sql).toBe('SELECT * FROM t WHERE id = :user_id')
    expect(result.defaults).toEqual({})
  })

  it('将 {{var=默认}} 转为 :var 并提取默认值', () => {
    const result = convertMetabaseSql('SELECT * FROM t WHERE d >= {{start_date=2026-01-01}}')
    expect(result.sql).toBe('SELECT * FROM t WHERE d >= :start_date')
    expect(result.defaults).toEqual({ start_date: '2026-01-01' })
  })

  it('可选块去掉 [[ ]] 并加注释，内层变量一并转换', () => {
    const result = convertMetabaseSql(
      'SELECT * FROM t WHERE 1=1\n[[ AND city = {{city_filter}} ]]\nORDER BY 1',
    )
    expect(result.changed).toBe(true)
    expect(result.sql).toContain('-- omni: 原 Metabase 可选块，已始终保留')
    expect(result.sql).not.toContain('[[')
    expect(result.sql).not.toContain(']]')
    expect(result.sql).toContain('AND city = :city_filter')
    expect(result.sql).toContain('ORDER BY 1')
  })

  it('字符串字面量内的 {{ 不替换', () => {
    const sql = "SELECT '{{user_id}}' AS tip, id FROM t WHERE id = {{user_id}}"
    const result = convertMetabaseSql(sql)
    expect(result.sql).toBe("SELECT '{{user_id}}' AS tip, id FROM t WHERE id = :user_id")
  })

  it('不支持的嵌套卡片 / Snippet 保留原文并告警', () => {
    const result = convertMetabaseSql(
      'SELECT * FROM {{#123}} x WHERE a = {{snippet: common}} AND b = {{ok}}',
    )
    expect(result.sql).toContain('{{#123}}')
    expect(result.sql).toContain('{{snippet: common}}')
    expect(result.sql).toContain(':ok')
    expect(result.warnings.length).toBeGreaterThanOrEqual(2)
    expect(result.changed).toBe(true)
  })

  it('非法参数名不替换并告警', () => {
    const result = convertMetabaseSql('SELECT {{坏名字}}')
    expect(result.sql).toContain('{{坏名字}}')
    expect(result.warnings.length).toBe(1)
  })
})
