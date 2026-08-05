/** Quartz 6 段 Cron 常用预设（秒 分 时 日 月 周） */
export const CRON_PRESET_CUSTOM = 'custom'

export interface CronPreset {
  value: string
  cron: string
  labelKey: string
}

export const CRON_PRESETS: CronPreset[] = [
  { value: 'daily9', cron: '0 0 9 * * ?', labelKey: 'subscriptions.cronDaily9' },
  { value: 'daily8', cron: '0 0 8 * * ?', labelKey: 'subscriptions.cronDaily8' },
  { value: 'daily18', cron: '0 0 18 * * ?', labelKey: 'subscriptions.cronDaily18' },
  { value: 'weekday9', cron: '0 0 9 ? * MON-FRI', labelKey: 'subscriptions.cronWeekday9' },
  { value: 'monday9', cron: '0 0 9 ? * MON', labelKey: 'subscriptions.cronMonday9' },
  { value: 'hourly', cron: '0 0 * * * ?', labelKey: 'subscriptions.cronHourly' },
  { value: 'every30m', cron: '0 */30 * * * ?', labelKey: 'subscriptions.cronEvery30m' },
  { value: CRON_PRESET_CUSTOM, cron: '', labelKey: 'subscriptions.cronCustom' },
]

/** 根据 Cron 反查预设；无匹配则视为自定义。 */
export function resolveCronPreset(cron: string | undefined | null): string {
  const value = (cron || '').trim()
  if (!value) return 'daily9'
  const hit = CRON_PRESETS.find((item) => item.value !== CRON_PRESET_CUSTOM && item.cron === value)
  return hit?.value ?? CRON_PRESET_CUSTOM
}

/** 预设对应的 Cron；自定义时返回空串。 */
export function cronFromPreset(preset: string): string {
  const hit = CRON_PRESETS.find((item) => item.value === preset)
  return hit && hit.value !== CRON_PRESET_CUSTOM ? hit.cron : ''
}
