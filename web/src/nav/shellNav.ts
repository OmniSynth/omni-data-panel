import type { InjectionKey } from 'vue'

/** AppShell 提供：刷新侧边栏集合树 */
export const refreshShellNavKey: InjectionKey<() => Promise<void>> = Symbol('refreshShellNav')
