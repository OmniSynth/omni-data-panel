import { computed, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

/** localStorage 中保存界面语言的键名 */
export const LOCALE_KEY = 'omni.locale'
/** 应用支持的界面语言 */
export type AppLocale = 'zh-CN' | 'en-US'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const

/** 从本地存储读取语言；非法值回落中文 */
function readStoredLocale(): AppLocale {
  const raw = localStorage.getItem(LOCALE_KEY)
  return raw === 'en-US' ? 'en-US' : 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages,
})

/** 当前语言的响应式引用，供语言切换器与 Element Plus locale 使用 */
const localeRef = ref<AppLocale>(i18n.global.locale.value as AppLocale)

export const currentLocale = localeRef

/** @returns 当前界面语言 */
export function getLocale(): AppLocale {
  return localeRef.value
}

/**
 * 切换界面语言并持久化到 localStorage，同时更新 `document.documentElement.lang`。
 * @param locale 目标语言
 */
export function setLocale(locale: AppLocale) {
  localeRef.value = locale
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale === 'zh-CN' ? 'zh-CN' : 'en'
}

/** Element Plus 组件库语言包（随 currentLocale 切换） */
export const elementLocale = computed(() => (localeRef.value === 'en-US' ? en : zhCn))

/**
 * 在 setup 外翻译文案（如 api.ts、display.ts）。
 * @param key 消息键
 * @param named 插值参数
 */
export function t(key: string, named?: Record<string, unknown>) {
  return named ? i18n.global.t(key, named) : i18n.global.t(key)
}

setLocale(localeRef.value)

export default i18n
