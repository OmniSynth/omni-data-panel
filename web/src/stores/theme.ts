import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** localStorage 中保存主题的键名 */
export const THEME_KEY = 'omni.theme'

/** 应用支持的主题 */
export type AppTheme = 'light' | 'dark'

function readStoredTheme(): AppTheme {
  const raw = localStorage.getItem(THEME_KEY)
  return raw === 'dark' ? 'dark' : 'light'
}

/** 将主题同步到 documentElement（Element Plus 依赖 html.dark） */
export function applyThemeClass(theme: AppTheme) {
  const root = document.documentElement
  root.classList.toggle('dark', theme === 'dark')
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

applyThemeClass(readStoredTheme())

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<AppTheme>(readStoredTheme())
  const isDark = computed(() => theme.value === 'dark')

  function setTheme(next: AppTheme) {
    theme.value = next
    localStorage.setItem(THEME_KEY, next)
    applyThemeClass(next)
  }

  function toggle() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, isDark, setTheme, toggle }
})
