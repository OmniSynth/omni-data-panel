import type { ResourceType } from './types'

export function resourcePath(type: ResourceType, id: string | number): string {
  const key = String(id)
  switch (type) {
    case 'QUESTION':
      return `/questions/${key}`
    case 'DASHBOARD':
      return `/dashboards/${key}/view`
    case 'MODEL':
      return `/models/${key}`
    case 'METRIC':
      return `/metrics?id=${key}`
    case 'COLLECTION':
      return `/collections/${key}`
    default:
      return '/'
  }
}

export function resourceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    QUESTION: '问题',
    DASHBOARD: '仪表盘',
    MODEL: '模型',
    METRIC: '指标',
    COLLECTION: '集合',
  }
  return labels[type] || type
}
