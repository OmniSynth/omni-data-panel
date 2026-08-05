import { format, type SqlLanguage } from 'sql-formatter'
import type { SqlDialectId } from '@/sql/dialects'
import { resolveSqlDialect } from '@/sql/dialects'

const dialectLanguage: Record<SqlDialectId, SqlLanguage> = {
  MYSQL: 'mysql',
  MARIADB: 'mariadb',
  POSTGRESQL: 'postgresql',
  MSSQL: 'transactsql',
  ORACLE: 'plsql',
  CLICKHOUSE: 'sql',
  HIVE: 'sql',
  SPARK: 'sql',
  SQLITE: 'sqlite',
  GENERIC: 'sql',
}

/** 按数据源方言格式化 SQL；空串原样返回。 */
export function formatSql(sql: string, dialect?: string | null, jdbcUrl?: string | null): string {
  const trimmed = sql.trim()
  if (!trimmed) return sql
  const adapter = resolveSqlDialect(dialect, jdbcUrl)
  return format(sql, {
    language: dialectLanguage[adapter.id],
    tabWidth: 2,
    keywordCase: 'upper',
    dataTypeCase: 'upper',
    functionCase: 'upper',
  })
}
