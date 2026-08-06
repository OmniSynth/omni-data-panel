import { createApp, type Directive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'gridstack/dist/gridstack.min.css'
import './styles.css'
import App from './App.vue'
import router from './router'
import i18n, { elementLocale } from './i18n'
import { useUserStore } from './stores/user'
import { useThemeStore } from './stores/theme'
import { useFontSizeStore } from './stores/fontSize'

const permission: Directive<HTMLElement, string> = {
  mounted(element, binding) {
    if (!useUserStore().hasPermission(binding.value)) element.remove()
  },
}

const app = createApp(App)
  .use(createPinia())
  .use(router)
  .use(i18n)
  .use(ElementPlus, { size: 'default', locale: elementLocale.value })
  .directive('permission', permission)

useThemeStore()
useFontSizeStore()
app.mount('#app')
