import { t } from '@/i18n'

/** 将毫秒耗时格式化为可读文本。 */
export function formatDuration(ms?: number | null) {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return ''
  if (ms < 1000) return `${Math.round(ms)} ms`
  if (ms < 60_000) return t('duration.seconds', { n: (ms / 1000).toFixed(2) })
  const minutes = Math.floor(ms / 60_000)
  const seconds = ((ms % 60_000) / 1000).toFixed(1)
  return t('duration.minutesSeconds', { m: minutes, s: seconds })
}
