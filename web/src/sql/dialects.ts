import {
  MySQL,
  PostgreSQL,
  SQLite,
  MSSQL,
  MariaSQL,
  StandardSQL,
  type SQLDialect,
} from '@codemirror/lang-sql'

/** 与后端 DialectPlugin 编码对齐；编辑器可多于运行时已注册方言。 */
export type SqlDialectId = 'MYSQL' | 'MARIADB' | 'POSTGRESQL' | 'SQLITE' | 'MSSQL' | 'GENERIC'

export interface SqlDialectAdapter {
  id: SqlDialectId
  label: string
  /** CodeMirror 方言，决定关键字与内建补全行为。 */
  language: SQLDialect
  /** 根据 JDBC URL 识别该方言。 */
  matchJdbcUrl?: (jdbcUrl: string) => boolean
}

const adapters = new Map<SqlDialectId, SqlDialectAdapter>()

export function registerSqlDialect(adapter: SqlDialectAdapter) {
  adapters.set(adapter.id, adapter)
}

export function listSqlDialects(): SqlDialectAdapter[] {
  return [...adapters.values()]
}

export function resolveSqlDialect(id?: string | null, jdbcUrl?: string | null): SqlDialectAdapter {
  const normalized = (id || '').trim().toUpperCase() as SqlDialectId
  if (normalized && adapters.has(normalized)) {
    return adapters.get(normalized)!
  }
  if (jdbcUrl) {
    for (const adapter of adapters.values()) {
      if (adapter.matchJdbcUrl?.(jdbcUrl)) return adapter
    }
  }
  return adapters.get('GENERIC')!
}

registerSqlDialect({
  id: 'MYSQL',
  label: 'MySQL',
  language: MySQL,
  matchJdbcUrl: (url) => /^jdbc:mysql:/i.test(url),
})

registerSqlDialect({
  id: 'MARIADB',
  label: 'MariaDB',
  language: MariaSQL,
  matchJdbcUrl: (url) => /^jdbc:mariadb:/i.test(url),
})

registerSqlDialect({
  id: 'POSTGRESQL',
  label: 'PostgreSQL',
  language: PostgreSQL,
  matchJdbcUrl: (url) => /^jdbc:postgresql:/i.test(url),
})

registerSqlDialect({
  id: 'SQLITE',
  label: 'SQLite',
  language: SQLite,
  matchJdbcUrl: (url) => /^jdbc:sqlite:/i.test(url),
})

registerSqlDialect({
  id: 'MSSQL',
  label: 'SQL Server',
  language: MSSQL,
  matchJdbcUrl: (url) => /^jdbc:(sqlserver|jtds:sqlserver):/i.test(url),
})

registerSqlDialect({
  id: 'GENERIC',
  label: '通用 SQL',
  language: StandardSQL,
})
