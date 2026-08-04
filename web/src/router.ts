import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppShell from '@/layout/AppShell.vue'
import AdminShell from '@/layout/AdminShell.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/public/dashboard/:token',
      component: () => import('@/views/PublicDashboardView.vue'),
      meta: { public: true },
    },
    {
      path: '/public/question/:token',
      component: () => import('@/views/PublicQuestionView.vue'),
      meta: { public: true },
    },
    {
      path: '/embed/dashboard/:token',
      component: () => import('@/views/EmbedDashboardView.vue'),
      meta: { public: true },
    },
    {
      path: '/embed/question/:token',
      component: () => import('@/views/EmbedQuestionView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', component: () => import('@/views/HomeView.vue') },
        { path: 'collections/:id', component: () => import('@/views/CollectionView.vue') },
        { path: 'questions', component: () => import('@/views/ChartsView.vue') },
        { path: 'questions/:id', component: () => import('@/views/QuestionView.vue') },
        { path: 'models', component: () => import('@/views/DatasetsView.vue') },
        { path: 'models/:id', component: () => import('@/views/DatasetsView.vue') },
        { path: 'metrics', component: () => import('@/views/MetricsView.vue') },
        { path: 'databases', component: () => import('@/views/DataBrowserView.vue') },
        { path: 'sql', component: () => import('@/views/SqlQueryView.vue'), meta: { permission: 'query:raw' } },
        { path: 'query', component: () => import('@/views/QueryWorkbenchView.vue'), meta: { permission: 'query:execute' } },
        { path: 'dashboards/:id/edit', component: () => import('@/views/DashboardEditorView.vue') },
        { path: 'dashboards/:id/view', component: () => import('@/views/DashboardView.vue') },
        { path: 'trash', component: () => import('@/views/TrashView.vue') },
        { path: 'search', component: () => import('@/views/SearchView.vue') },
      ],
    },
    {
      path: '/admin',
      component: AdminShell,
      meta: { admin: true },
      children: [
        { path: '', redirect: '/admin/settings' },
        { path: 'settings', component: () => import('@/views/admin/SettingsView.vue') },
        { path: 'databases', component: () => import('@/views/DataSourcesView.vue') },
        { path: 'data-source-health', component: () => import('@/views/admin/DataSourceHealthView.vue') },
        { path: 'query-audits', component: () => import('@/views/admin/QueryAuditsView.vue') },
        { path: 'login-audits', component: () => import('@/views/admin/LoginAuditsView.vue') },
        { path: 'users', component: () => import('@/views/UsersView.vue') },
        { path: 'roles', component: () => import('@/views/RolesView.vue') },
        { path: 'permissions', component: () => import('@/views/PermissionsView.vue'), meta: { permission: 'dataset:manage' } },
        { path: 'subscriptions', component: () => import('@/views/SubscriptionsView.vue'), meta: { permission: 'schedule:manage' } },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (to.meta.public) {
    if (store.token && to.path === '/login') return '/'
    return true
  }
  if (!store.token) return { path: '/login', query: { redirect: to.fullPath } }
  try {
    if (!store.user) await store.loadUser()
  } catch {
    store.logout()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.matched.some((record) => record.meta.admin) && !store.isAdmin) return '/'
  const permission = to.matched.map((record) => record.meta.permission as string | undefined).find(Boolean)
  return permission && !store.hasPermission(permission) ? '/' : true
})

export default router
