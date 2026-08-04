import { createApp, type Directive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'gridstack/dist/gridstack.min.css'
import './styles.css'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'

const permission: Directive<HTMLElement, string> = {
  mounted(element, binding) {
    if (!useUserStore().hasPermission(binding.value)) element.remove()
  },
}

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: zhCn, size: 'default' })
  .directive('permission', permission)
  .mount('#app')
