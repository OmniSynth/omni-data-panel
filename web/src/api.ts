import axios, { type AxiosRequestConfig } from 'axios'
import type {
  AdminUser, AuditCleanupPayload, Chart, Collection, CollectionItem, CompletionSchema, Dashboard, DashboardCard,
  DashboardRender, DataSource, DataSourceHealthOverview, Dataset, DialectInfo, ExportTask, Id, LoginAudit,
  Metric, MetadataColumn, MetadataTable, PageResult, Permission, PublicLink, PublicQuestion,
  PublicResourceType, QueryAudit, QuerySnapshot, QuerySubmission, QuerySubmitResult, RecentItem,
  ResourceType, Role, RoleResourceGrant, SearchHit, SiteSettings, Subscription, TrashItem, User,
} from './types'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export const TOKEN_KEY = 'omni_data_token'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      if (!location.pathname.startsWith('/login')
        && !location.pathname.startsWith('/public/')
        && !location.pathname.startsWith('/embed/')) {
        location.href = '/login'
      }
    }
    return Promise.reject(new Error(error.response?.data?.message || error.message || '网络请求失败'))
  },
)

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  const body = response.data
  if (body.code !== 0 && body.code !== 200) throw new Error(body.message || '请求失败')
  return body.data
}

const crud = <T>(path: string) => ({
  list: () => request<T[]>({ url: path }),
  get: (id: Id) => request<T>({ url: `${path}/${String(id)}` }),
  create: (data: Partial<T>) => request<T>({ url: path, method: 'POST', data }),
  update: (id: Id, data: Partial<T>) => request<T>({ url: `${path}/${String(id)}`, method: 'PUT', data }),
  remove: (id: Id) => request<void>({ url: `${path}/${String(id)}`, method: 'DELETE' }),
})

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request<{ accessToken: string; tokenType: string }>({ url: '/auth/login', method: 'POST', data }),
  me: () => request<User>({ url: '/auth/me' }),
  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    request<void>({ url: '/auth/password', method: 'PUT', data }),
}

export const dataSourceApi = {
  ...crud<DataSource>('/data-sources'),
  dialects: () => request<DialectInfo[]>({ url: '/data-sources/dialects' }),
  test: (id: Id) => request<void>({ url: `/data-sources/${String(id)}/test`, method: 'POST' }),
  sync: (id: Id) => request<void>({ url: `/data-sources/${String(id)}/metadata/sync`, method: 'POST' }),
  schemas: (id: Id) => request<string[]>({ url: `/data-sources/${String(id)}/metadata/schemas` }),
  tables: (id: Id, schema: string) => request<MetadataTable[]>({
    url: `/data-sources/${String(id)}/metadata/schemas/${encodeURIComponent(schema)}/tables`,
  }),
  columns: (id: Id, schema: string, table: string) => request<MetadataColumn[]>({
    url: `/data-sources/${String(id)}/metadata/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/columns`,
  }),
  completionSchema: (id: Id) => request<CompletionSchema>({
    url: `/data-sources/${String(id)}/metadata/completion-schema`,
  }),
}

type DatasetSave = Pick<Dataset, 'name' | 'dataSourceId' | 'schemaName' | 'tableName' | 'fields'>
  & Partial<Pick<Dataset, 'description' | 'collectionId' | 'modelType' | 'definitionSql'>>
const datasetSave = (data: Partial<Dataset>): DatasetSave => ({
  name: data.name || '',
  dataSourceId: String(data.dataSourceId),
  schemaName: data.schemaName || '',
  tableName: data.tableName || '',
  fields: (data.fields || []).map(({ name, columnName, fieldType, aggregation }) => ({
    name, columnName, fieldType, aggregation,
  })),
  description: data.description,
  collectionId: data.collectionId === undefined ? undefined : String(data.collectionId),
  modelType: data.modelType,
  definitionSql: data.definitionSql,
})

export const datasetApi = {
  list: () => request<Dataset[]>({ url: '/datasets' }),
  get: (id: Id) => request<Dataset>({ url: `/datasets/${String(id)}` }),
  create: (data: Partial<Dataset>) => request<Dataset>({
    url: '/datasets', method: 'POST', data: datasetSave(data),
  }),
  update: (id: Id, data: Partial<Dataset>) => request<Dataset>({
    url: `/datasets/${String(id)}`, method: 'PUT', data: datasetSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/datasets/${String(id)}`, method: 'DELETE' }),
}

type ChartSave = Pick<Chart, 'name' | 'queryJson' | 'chartType' | 'configJson'>
  & Partial<Pick<Chart, 'datasetId' | 'dataSourceId' | 'description' | 'collectionId'>>
const chartSave = (data: Partial<Chart>): ChartSave => ({
  name: data.name || '',
  datasetId: data.datasetId === undefined ? undefined : String(data.datasetId),
  dataSourceId: data.dataSourceId === undefined ? undefined : String(data.dataSourceId),
  queryJson: data.queryJson || '',
  chartType: data.chartType || '',
  configJson: data.configJson || '',
  description: data.description,
  collectionId: data.collectionId === undefined ? undefined : String(data.collectionId),
})

export const chartApi = {
  list: () => request<Chart[]>({ url: '/charts' }),
  get: (id: Id) => request<Chart>({ url: `/charts/${String(id)}` }),
  create: (data: Partial<Chart>) => request<Chart>({ url: '/charts', method: 'POST', data: chartSave(data) }),
  update: (id: Id, data: Partial<Chart>) => request<Chart>({
    url: `/charts/${String(id)}`, method: 'PUT', data: chartSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/charts/${String(id)}`, method: 'DELETE' }),
}

type DashboardSave = Pick<Dashboard, 'name' | 'configJson'>
  & Partial<Pick<Dashboard, 'description' | 'collectionId'>>
const dashboardSave = (data: Partial<Dashboard>): DashboardSave => ({
  name: data.name || '',
  configJson: data.configJson || '{}',
  description: data.description,
  collectionId: data.collectionId === undefined ? undefined : String(data.collectionId),
})

export const dashboardApi = {
  list: () => request<Dashboard[]>({ url: '/dashboards' }),
  get: (id: Id) => request<Dashboard>({ url: `/dashboards/${String(id)}` }),
  create: (data: Partial<Dashboard>) => request<Dashboard>({
    url: '/dashboards', method: 'POST', data: dashboardSave(data),
  }),
  update: (id: Id, data: Partial<Dashboard>) => request<Dashboard>({
    url: `/dashboards/${String(id)}`, method: 'PUT', data: dashboardSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/dashboards/${String(id)}`, method: 'DELETE' }),
  cards: (id: Id) => request<DashboardCard[]>({ url: `/dashboards/${String(id)}/cards` }),
  render: (id: Id) => request<DashboardRender>({ url: `/dashboards/${String(id)}/render` }),
  createCard: (id: Id, data: Pick<DashboardCard, 'chartId' | 'title' | 'layoutJson'>) =>
    request<DashboardCard>({
      url: `/dashboards/${String(id)}/cards`,
      method: 'POST',
      data: { ...data, chartId: String(data.chartId) },
    }),
  updateCard: (id: Id, cardId: Id, data: Pick<DashboardCard, 'chartId' | 'title' | 'layoutJson'>) =>
    request<DashboardCard>({
      url: `/dashboards/${String(id)}/cards/${String(cardId)}`,
      method: 'PUT',
      data: { ...data, chartId: String(data.chartId) },
    }),
  removeCard: (id: Id, cardId: Id) =>
    request<void>({ url: `/dashboards/${String(id)}/cards/${String(cardId)}`, method: 'DELETE' }),
}

export const queryApi = {
  submit: (submission: QuerySubmission) => request<QuerySubmitResult>({
    url: '/queries',
    method: 'POST',
    data: {
      ...submission,
      sourceId: submission.sourceId === undefined ? undefined : String(submission.sourceId),
      query: submission.query ? { ...submission.query, datasetId: String(submission.query.datasetId) } : undefined,
    },
  }),
  status: (id: Id) => request<QuerySnapshot>({ url: `/queries/${String(id)}` }),
  cancel: (id: Id) => request<void>({ url: `/queries/${String(id)}/cancel`, method: 'POST' }),
}

export const queryAuditApi = {
  page: (params: {
    keyword?: string
    status?: string
    userId?: Id
    sourceId?: Id
    fromTime?: string
    toTime?: string
    page?: number
    size?: number
  }) => request<PageResult<QueryAudit>>({
    url: '/admin/query-audits',
    params: {
      ...params,
      userId: params.userId === undefined ? undefined : String(params.userId),
      sourceId: params.sourceId === undefined ? undefined : String(params.sourceId),
    },
  }),
  detail: (id: Id) => request<QueryAudit>({ url: `/admin/query-audits/${String(id)}` }),
  cleanup: (data: AuditCleanupPayload) =>
    request<{ deleted: number }>({ url: '/admin/query-audits/cleanup', method: 'POST', data }),
}

export const loginAuditApi = {
  page: (params: {
    keyword?: string
    success?: boolean
    fromTime?: string
    toTime?: string
    page?: number
    size?: number
  }) => request<PageResult<LoginAudit>>({ url: '/admin/login-audits', params }),
  cleanup: (data: AuditCleanupPayload) =>
    request<{ deleted: number }>({ url: '/admin/login-audits/cleanup', method: 'POST', data }),
}

export const dataSourceHealthApi = {
  overview: () => request<DataSourceHealthOverview>({ url: '/admin/data-source-health' }),
}

export const roleApi = {
  list: () => request<Role[]>({ url: '/roles' }),
  permissions: () => request<Permission[]>({ url: '/permissions' }),
  create: (data: { code: string; name: string; description?: string; enabled: boolean }) =>
    request<Role>({ url: '/roles', method: 'POST', data }),
  update: (id: Id, data: { name: string; description?: string; enabled: boolean }) =>
    request<Role>({ url: `/roles/${String(id)}`, method: 'PUT', data }),
  remove: (id: Id) => request<void>({ url: `/roles/${String(id)}`, method: 'DELETE' }),
  savePermissions: (id: Id, permissionCodes: string[]) =>
    request<Role>({ url: `/roles/${String(id)}/permissions`, method: 'PUT', data: { permissionCodes } }),
}

export const userApi = {
  list: () => request<AdminUser[]>({ url: '/users' }),
  create: (data: { username: string; password: string; displayName: string; roleIds: Id[] }) =>
    request<AdminUser>({ url: '/users', method: 'POST', data: { ...data, roleIds: data.roleIds.map(String) } }),
  update: (id: Id, data: { displayName: string; enabled: boolean; roleIds: Id[] }) =>
    request<AdminUser>({
      url: `/users/${String(id)}`,
      method: 'PUT',
      data: { ...data, roleIds: data.roleIds.map(String) },
    }),
  resetPassword: (id: Id, password: string) =>
    request<void>({ url: `/users/${String(id)}/password`, method: 'PUT', data: { password } }),
}

export const resourcePermissionApi = {
  list: (resourceType: string, resourceId: Id) =>
    request<RoleResourceGrant[]>({ url: `/resources/${resourceType}/${String(resourceId)}/permissions` }),
  grant: (resourceType: string, resourceId: Id, roleId: Id, permission: 'READ' | 'WRITE') =>
    request<void>({
      url: `/resources/${resourceType}/${String(resourceId)}/permissions`,
      method: 'PUT',
      data: { roleId: String(roleId), permission },
    }),
  revoke: (resourceType: string, resourceId: Id, roleId: Id) =>
    request<void>({
      url: `/resources/${resourceType}/${String(resourceId)}/permissions/${String(roleId)}`,
      method: 'DELETE',
    }),
}

export const dataPolicyApi = {
  saveField: (datasetId: Id, data: { userId: Id; fieldName: string; allowed: boolean }) =>
    request<void>({
      url: `/datasets/${String(datasetId)}/policies/fields`,
      method: 'POST',
      data: { ...data, userId: String(data.userId) },
    }),
  deleteField: (datasetId: Id, userId: Id, fieldName: string) =>
    request<void>({
      url: `/datasets/${String(datasetId)}/policies/fields/${String(userId)}/${encodeURIComponent(fieldName)}`,
      method: 'DELETE',
    }),
  createRow: (datasetId: Id, data: { userId?: Id; name: string; ruleJson: string }) =>
    request<void>({
      url: `/datasets/${String(datasetId)}/policies/rows`,
      method: 'POST',
      data: { ...data, userId: data.userId === undefined ? undefined : String(data.userId) },
    }),
  deleteRow: (datasetId: Id, ruleId: Id) =>
    request<void>({ url: `/datasets/${String(datasetId)}/policies/rows/${String(ruleId)}`, method: 'DELETE' }),
}

const subscriptionSave = (data: Partial<Subscription>) => ({
  name: data.name || '',
  dashboardId: String(data.dashboardId),
  cronExpression: data.cronExpression || '',
  recipients: data.recipients || '',
  enabled: data.enabled ?? true,
})

export const subscriptionApi = {
  list: () => request<Subscription[]>({ url: '/subscriptions' }),
  get: (id: Id) => request<Subscription>({ url: `/subscriptions/${String(id)}` }),
  create: (data: Partial<Subscription>) => request<Subscription>({
    url: '/subscriptions', method: 'POST', data: subscriptionSave(data),
  }),
  update: (id: Id, data: Partial<Subscription>) => request<Subscription>({
    url: `/subscriptions/${String(id)}`, method: 'PUT', data: subscriptionSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/subscriptions/${String(id)}`, method: 'DELETE' }),
}

export const exportApi = {
  create: (data: { queryId: string; format: 'CSV' | 'XLSX' }) =>
    request<{ taskId: string }>({ url: '/exports', method: 'POST', data }),
  status: (id: Id) => request<ExportTask>({ url: `/exports/${String(id)}` }),
  downloadTask: async (id: Id) => {
    const response = await http.get<Blob>(`/exports/${String(id)}/download`, { responseType: 'blob' })
    return response.data
  },
  download: async (queryId: string, format: 'CSV' | 'XLSX') => {
    const response = await http.get<Blob>(`/exports/queries/${queryId}`, {
      params: { format },
      responseType: 'blob',
    })
    return response.data
  },
}

type CollectionSave = Pick<Collection, 'name'> & Partial<Pick<Collection, 'description' | 'parentId'>>
const collectionSave = (data: Partial<Collection>): CollectionSave => ({
  name: data.name || '',
  description: data.description,
  parentId: data.parentId === undefined ? undefined : String(data.parentId),
})

export const collectionApi = {
  tree: () => request<Collection[]>({ url: '/collections' }),
  create: (data: Partial<Collection>) => request<Collection>({
    url: '/collections', method: 'POST', data: collectionSave(data),
  }),
  update: (id: Id, data: Partial<Collection>) => request<Collection>({
    url: `/collections/${String(id)}`, method: 'PUT', data: collectionSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/collections/${String(id)}`, method: 'DELETE' }),
  items: (id: Id) => request<CollectionItem[]>({ url: `/collections/${String(id)}/items` }),
  move: (data: { resourceType: ResourceType; resourceId: Id; collectionId: Id }) =>
    request<void>({
      url: '/collections/move',
      method: 'PUT',
      data: {
        resourceType: data.resourceType,
        resourceId: String(data.resourceId),
        collectionId: String(data.collectionId),
      },
    }),
}

export const recentApi = {
  list: () => request<RecentItem[]>({ url: '/recents' }),
}

export const searchApi = {
  search: (q: string) => request<SearchHit[]>({ url: '/search', params: { q } }),
}

type MetricSave = Pick<Metric, 'name' | 'modelId' | 'expressionJson' | 'aggregation'>
  & Partial<Pick<Metric, 'description' | 'collectionId'>>
const metricSave = (data: Partial<Metric>): MetricSave => ({
  name: data.name || '',
  modelId: String(data.modelId),
  expressionJson: data.expressionJson || '{}',
  aggregation: data.aggregation || 'SUM',
  description: data.description,
  collectionId: data.collectionId === undefined ? undefined : String(data.collectionId),
})

export const metricApi = {
  list: () => request<Metric[]>({ url: '/metrics' }),
  get: (id: Id) => request<Metric>({ url: `/metrics/${String(id)}` }),
  create: (data: Partial<Metric>) => request<Metric>({
    url: '/metrics', method: 'POST', data: metricSave(data),
  }),
  update: (id: Id, data: Partial<Metric>) => request<Metric>({
    url: `/metrics/${String(id)}`, method: 'PUT', data: metricSave(data),
  }),
  remove: (id: Id) => request<void>({ url: `/metrics/${String(id)}`, method: 'DELETE' }),
}

export const trashApi = {
  list: () => request<TrashItem[]>({ url: '/trash' }),
  restore: (data: { resourceType: ResourceType; resourceId: Id }) =>
    request<void>({
      url: '/trash/restore',
      method: 'POST',
      data: { resourceType: data.resourceType, resourceId: String(data.resourceId) },
    }),
  purge: (data: { resourceType: ResourceType; resourceId: Id }) =>
    request<void>({
      url: '/trash',
      method: 'DELETE',
      data: { resourceType: data.resourceType, resourceId: String(data.resourceId) },
    }),
}

export const publicLinkApi = {
  list: (params?: { resourceType?: PublicResourceType; resourceId?: Id }) =>
    request<PublicLink[]>({
      url: '/public-links',
      params: params?.resourceId === undefined
        ? params
        : { ...params, resourceId: String(params.resourceId) },
    }),
  create: (data: { resourceType: PublicResourceType; resourceId: Id }) =>
    request<PublicLink>({
      url: '/public-links',
      method: 'POST',
      data: { resourceType: data.resourceType, resourceId: String(data.resourceId) },
    }),
  revoke: (id: Id) => request<void>({ url: `/public-links/${String(id)}`, method: 'DELETE' }),
}

export const publicApi = {
  dashboard: (token: string) => request<DashboardRender>({ url: `/public/dashboards/${token}` }),
  question: (token: string) => request<PublicQuestion>({ url: `/public/questions/${token}` }),
}

export const embedApi = {
  dashboard: (token: string) => request<DashboardRender>({ url: `/embed/dashboards/${token}` }),
  question: (token: string) => request<PublicQuestion>({ url: `/embed/questions/${token}` }),
}

export const settingsApi = {
  get: () => request<SiteSettings>({ url: '/settings' }),
  update: (data: SiteSettings) => request<SiteSettings>({ url: '/settings', method: 'PUT', data }),
}
