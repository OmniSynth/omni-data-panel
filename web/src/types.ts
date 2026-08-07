export type Id = number | string
export type RecordData = Record<string, unknown>

export type ResourceType = 'QUESTION' | 'DASHBOARD' | 'MODEL' | 'METRIC' | 'COLLECTION'
export type PublicResourceType = 'DASHBOARD' | 'QUESTION'
export type ModelType = 'TABLE' | 'SQL'

export interface User {
  id: Id
  username: string
  displayName: string
  email?: string
  roles: string[]
  admin: boolean
  permissions: string[]
}

export interface Role {
  id: Id
  code: string
  name: string
  description?: string
  enabled: boolean
  builtIn?: boolean
  permissions?: string[]
}

export interface Permission {
  id: Id
  code: string
  name: string
}

export interface AdminUser {
  id: Id
  username: string
  displayName: string
  email?: string
  enabled: boolean
  activated: boolean
  totpEnabled?: boolean
  roleIds: Id[]
  roles: string[]
  permissions: string[]
}

export interface UserDirectoryItem {
  id: Id
  username: string
  displayName: string
  email?: string | null
}

export interface FieldPermissionRow {
  fieldName: string
  allowed: boolean
}

export interface RowRule {
  id: Id
  datasetId: Id
  userId?: Id | null
  name: string
  ruleJson: string
  enabled: boolean
}

export type FilterOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN'

export interface FilterCondition {
  field: string
  operator: FilterOperator
  value: string
}

export interface RoleResourceGrant {
  roleId: Id
  code: string
  name: string
  permission: 'READ' | 'WRITE'
}

export interface ObjectAclTableRef {
  schemaName: string
  tableName: string
}

export interface ObjectAclColumnRef {
  schemaName: string
  tableName: string
  columnName: string
}

export interface DataSourceObjectAcl {
  roleId: Id
  tables: ObjectAclTableRef[]
  columns: ObjectAclColumnRef[]
}

export interface DataSource {
  id: Id
  name: string
  host?: string
  port?: number
  /** 可选默认库；未填则同步并浏览全部业务库 */
  defaultDatabase?: string | null
  /** 组装后的 JDBC 地址（管理员可见） */
  jdbcUrl?: string
  username?: string
  password?: string
  status?: string
  ownerId?: Id
  /** 运行时方言编码，须为已注册插件 */
  dialect?: string
}

/** 后端已注册、可创建连接的方言 */
export interface DialectInfo {
  code: string
  label: string
  defaultPort: number
}

export interface CompletionSchema {
  dialect: string
  schemas: Record<string, Record<string, string[]>>
}

export interface MetadataTable {
  tableName: string
  comment?: string
}

export interface MetadataColumn {
  columnName: string
  typeName: string
  columnSize?: number | null
  decimalDigits?: number | null
  nullable: boolean
  primaryKey?: boolean
  foreignKey?: boolean
  fkTableName?: string | null
  fkColumnName?: string | null
  position: number
  comment?: string
}

export interface DatasetField {
  id?: Id
  datasetId?: Id
  name: string
  columnName: string
  fieldType: 'DIMENSION' | 'METRIC'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
}

export interface Dataset {
  id: Id
  name: string
  dataSourceId: Id
  schemaName: string
  tableName: string
  fields: DatasetField[]
  ownerId?: Id
  description?: string
  collectionId?: Id
  modelType?: ModelType
  definitionSql?: string
  deletedAt?: string
  updatedAt?: string
}

export interface SemanticQuery {
  datasetId: Id
  dimensions: string[]
  metrics: string[]
  /** 业务指标 bi_metric 标识 */
  metricIds?: Id[]
  filter?: QueryFilter
  sorts: Array<{ field: string; direction: 'ASC' | 'DESC' }>
  limit: number
}

export interface QueryFilter {
  logic?: 'AND' | 'OR'
  field?: string
  operator?: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN' | 'IS_NULL' | 'NOT_NULL'
  value?: unknown
  children?: QueryFilter[]
}

export interface QuerySubmission {
  sourceId?: Id
  sql?: string
  parameters?: unknown[]
  namedParameters?: Record<string, unknown>
  query?: SemanticQuery
}

export interface QuerySubmitResult {
  queryId: string
}

export interface QuerySnapshot {
  queryId: string
  userId?: Id
  sourceId?: Id
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  result?: QueryResult
  error?: string
  /** 提交时间（毫秒时间戳） */
  startedAtMs?: number
  /** 从提交到结束的耗时毫秒 */
  durationMs?: number
}

export interface QueryResult {
  columns: string[]
  rows: RecordData[]
  /** 真实命中行数；缺省时前端回退为 rows.length */
  total?: number
  /** 明细是否因 max-rows 触顶截断 */
  truncated?: boolean
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/** 分析数据源连接池健康状态 */
export type DataSourceHealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'COLD' | 'DISABLED'

export interface DataSourceHealthItem {
  sourceId: Id
  name: string
  host?: string | null
  port?: number | null
  defaultDatabase?: string | null
  sourceStatus?: string | null
  health: DataSourceHealthStatus
  poolReady: boolean
  latencyMs?: number | null
  message?: string | null
  maximumPoolSize?: number | null
  minimumIdle?: number | null
  activeConnections?: number | null
  idleConnections?: number | null
  totalConnections?: number | null
  threadsAwaitingConnection?: number | null
  checkedAtMs: number
}

export interface DataSourceHealthOverview {
  checkedAt: string
  total: number
  up: number
  degraded: number
  down: number
  cold: number
  disabled: number
  items: DataSourceHealthItem[]
}

export interface QueryAudit {
  id: Id
  queryId: string
  userId: Id
  username: string
  displayName?: string
  dataSourceId: Id
  dataSourceName: string
  sqlText: string
  clientIp?: string
  userAgent?: string
  status: string
  rowCount?: number | null
  errorMessage?: string | null
  durationMs?: number | null
  resultPreview?: string | null
  startedAt?: string
  finishedAt?: string | null
}

export interface QueryAuditPreview {
  columns: string[]
  rows: RecordData[]
  previewRowCount: number
  totalRowCount: number
}

export interface AuditCleanupPayload {
  mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'
  days?: number
  before?: string
}

export interface LoginAudit {
  id: Id
  username: string
  userId?: Id | null
  success: boolean
  message: string
  clientIp?: string | null
  userAgent?: string | null
  loggedAt?: string
}

export interface SystemLogEntry {
  level: string
  loggerName: string
  message: string
  stackTrace?: string | null
  threadName?: string
  requestId?: string | null
  createdAt?: string
}

export interface SystemLogMeta {
  capacity: number
  buffered: number
}

export interface DatasetAudit {
  id: Id
  datasetId?: Id | null
  datasetName: string
  action: string
  operatorId?: Id | null
  operatorUsername?: string | null
  operatorDisplayName?: string | null
  detail?: string | null
  createdAt?: string
}

export interface ExportAudit {
  id: Id
  userId?: Id | null
  username?: string | null
  displayName?: string | null
  queryId?: string | null
  dataSourceId?: Id | null
  dataSourceName?: string | null
  format: string
  mode: string
  status: string
  rowCount?: number | null
  byteSize?: number | null
  taskId?: string | null
  clientIp?: string | null
  userAgent?: string | null
  errorMessage?: string | null
  createdAt?: string
}

export interface Chart {
  id: Id
  name: string
  datasetId?: Id
  dataSourceId?: Id
  queryJson: string
  chartType: string
  configJson: string
  ownerId?: Id
  description?: string
  collectionId?: Id
  deletedAt?: string
  updatedAt?: string
}

export interface DashboardCard {
  id: Id
  dashboardId: Id
  chartId: Id
  title: string
  layoutJson: string
  bindingsJson?: string
  clickActionJson?: string
}

export interface DashboardLayout {
  x: number
  y: number
  w: number
  h: number
  /** 所属 Tab；缺省归入第一个 Tab */
  tabId?: string
}

export interface DashboardTab {
  id: string
  name: string
}

export type DashboardParameterType =
  | 'text'
  | 'number'
  | 'date'
  | 'date-range'
  | 'select'
  | 'multi-select'

export interface DashboardParameterOption {
  label: string
  value: string | number
}

export interface ParameterOptionsFrom {
  datasetId: string | number
  field: string
  limit?: number
}

export interface DashboardParameter {
  id: string
  label: string
  type: DashboardParameterType
  defaultValue?: unknown
  options?: DashboardParameterOption[]
  optionsFrom?: ParameterOptionsFrom
  required?: boolean
}

export interface DashboardConfig {
  parameters?: DashboardParameter[]
  /** 仪表盘内多页签；缺省或空数组表示单页（兼容旧数据） */
  tabs?: DashboardTab[]
}

export interface CardParameterBinding {
  parameterId: string
  mode: 'semantic' | 'sql'
  field?: string
  operator?: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN'
  /** 兼容旧配置：裸 ? 下标 */
  parameterIndex?: number
  /** SQL 命名占位名（不含冒号），对应 :name */
  parameterName?: string
}

export interface CardClickAction {
  enabled: boolean
  setParameterId: string
  valueMode: 'replace' | 'toggle'
}

export interface Dashboard {
  id: Id
  name: string
  configJson: string
  ownerId: Id
  lastRefreshedAt?: string
  accessLevel: 'ADMIN' | 'OWNER' | 'WRITE' | 'READ'
  description?: string
  collectionId?: Id
  deletedAt?: string
  updatedAt?: string
}

export interface DashboardRenderCard {
  cardId: Id
  title: string
  chartType: string
  configJson: string
  layoutJson: string
  bindingsJson?: string
  clickActionJson?: string
  result?: QueryResult
  error?: string
}

export interface DashboardRender {
  id: Id
  name: string
  configJson: string
  accessLevel: 'ADMIN' | 'OWNER' | 'WRITE' | 'READ'
  /** 服务端实际用于查询的合并参数（默认值 + 登录覆盖 / 嵌入锁定） */
  parameterValues?: Record<string, unknown>
  cards: DashboardRenderCard[]
}

export interface ExportTask {
  id: Id
  ownerId: Id
  queryId: string
  format: 'CSV' | 'XLSX'
  status: string
  objectName?: string
  errorMessage?: string
}

export interface Subscription {
  id: Id
  name: string
  dashboardId: Id
  cronExpression: string
  recipientUserIds: Id[]
  recipientsLabel?: string
  enabled: boolean
  ownerId?: Id
}

export type ScheduleType = 'METADATA_SYNC' | 'DASHBOARD_REFRESH' | 'SUBSCRIPTION'

export interface Schedule {
  id: Id
  name: string
  scheduleType: ScheduleType
  targetId: Id
  cronExpression: string
  payloadJson?: string
  enabled: boolean
  ownerId?: Id
  lastRunAt?: string
}

export interface Collection {
  id: Id
  name: string
  description?: string
  parentId?: Id
  personalOwnerId?: Id
  ownerId?: Id
  archived?: boolean
  children?: Collection[]
  updatedAt?: string
}

export interface CollectionItem {
  id: Id
  type: ResourceType
  name: string
  description?: string
  updatedAt?: string
  ownerId?: Id
}

export interface RecentItem {
  resourceType: ResourceType
  resourceId: Id
  name: string
  description?: string
  visitedAt: string
}

export interface Metric {
  id: Id
  name: string
  description?: string
  modelId: Id
  expressionJson: string
  aggregation: string
  collectionId?: Id
  ownerId?: Id
  deletedAt?: string
  updatedAt?: string
}

export interface SearchHit {
  resourceType: ResourceType
  resourceId: Id
  name: string
  description?: string
  collectionId?: Id
}

export interface TrashItem {
  resourceType: ResourceType
  resourceId: Id
  name: string
  description?: string
  deletedAt: string
  ownerId?: Id
}

export interface PublicLink {
  id: Id
  resourceType: PublicResourceType
  resourceId: Id
  token: string
  enabled: boolean
  createdBy?: Id
  createdAt?: string
  /** 过期时间；空表示永不过期 */
  expiresAt?: string | null
}

/** 公开链接有效天数预设；0 / null 表示永不过期 */
export type PublicLinkExpireDays = 0 | 1 | 7 | 30 | 90 | null

export interface PublicQuestion {
  id: Id
  name: string
  description?: string
  chartType: string
  configJson: string
  result?: QueryResult
  error?: string
}

export interface SiteSettings {
  'site.name'?: string
  'embed.enabled'?: string | boolean
  'embed.allowed-origins'?: string
  'ui.sql.tips-collapsed-default'?: string | boolean
  'cache.query.enabled'?: string | boolean
  'cache.query.ttl-seconds'?: string | number
  'logs.clear.enabled'?: string | boolean
  'auth.session.max-concurrent'?: string | number
  'mail.host'?: string
  'mail.port'?: string | number
  'mail.username'?: string
  'mail.password.set'?: string | boolean
  'mail.from'?: string
  'mail.smtp.auth'?: string | boolean
  'mail.smtp.starttls'?: string | boolean
  'mail.ready'?: string | boolean
  [key: string]: string | boolean | number | undefined
}
