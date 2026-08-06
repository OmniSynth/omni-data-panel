import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** localStorage 中保存字号的键名 */
export const FONT_SIZE_KEY = 'omni.font-size'

/** 应用支持的字号档位 */
export type AppFontSize = 'small' | 'medium' | 'large'

const FONT_SIZES: readonly AppFontSize[] = ['small', 'medium', 'large']

function readStoredFontSize(): AppFontSize {
  const raw = localStorage.getItem(FONT_SIZE_KEY)
  return FONT_SIZES.includes(raw as AppFontSize) ? (raw as AppFontSize) : 'medium'
}

/** 将字号同步到 documentElement，供 styles.css 选择器与组件读取 */
export function applyFontSize(size: AppFontSize) {
  document.documentElement.dataset.fontSize = size
}

applyFontSize(readStoredFontSize())

export const useFontSizeStore = defineStore('fontSize', () => {
  const fontSize = ref<AppFontSize>(readStoredFontSize())
  const options = FONT_SIZES

  const labelKey = computed(() => `fontSize.${fontSize.value}` as const)

  function setFontSize(next: AppFontSize) {
    if (!FONT_SIZES.includes(next)) return
    fontSize.value = next
    localStorage.setItem(FONT_SIZE_KEY, next)
    applyFontSize(next)
  }

  return { fontSize, options, labelKey, setFontSize }
})
