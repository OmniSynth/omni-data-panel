import { describe, expect, it } from 'vitest'
import { formatSql } from '@/sql/format'

describe('formatSql', () => {
  it('空串原样返回', () => {
    expect(formatSql('')).toBe('')
    expect(formatSql('   ')).toBe('   ')
  })

  it('按 MySQL 方言格式化并大写关键字', () => {
    const result = formatSql('select id,name from users where id=:id', 'MYSQL')
    expect(result).toContain('SELECT')
    expect(result).toContain('FROM')
    expect(result).toContain('WHERE')
    expect(result).toContain(':id')
  })
})
