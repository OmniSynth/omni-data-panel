import type { ResourceType } from './types'
import { t } from '@/i18n'

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
  const keys = ['QUESTION', 'DASHBOARD', 'MODEL', 'METRIC', 'COLLECTION'] as const
  if ((keys as readonly string[]).includes(type)) return t(`nav.${type}`)
  return type
}
