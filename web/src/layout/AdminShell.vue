<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuGroups = [
  {
    label: '系统',
    items: [
      { path: '/admin/settings', label: '通用设置' },
      { path: '/admin/users', label: '用户' },
      { path: '/admin/roles', label: '角色' },
      { path: '/admin/subscriptions', label: '订阅' },
    ],
  },
  {
    label: '数据源',
    items: [
      { path: '/admin/databases', label: '数据源' },
      { path: '/admin/data-source-health', label: '连接池监控' },
    ],
  },
  {
    label: '审计',
    items: [
      { path: '/admin/query-audits', label: '查询审计' },
      { path: '/admin/login-audits', label: '登录日志' },
    ],
  },
  {
    label: '权限',
    items: [
      { path: '/admin/permissions', label: '数据权限' },
    ],
  },
]

const flatMenus = menuGroups.flatMap((group) => group.items)

const active = computed(() => {
  const hit = flatMenus.find((item) => route.path.startsWith(item.path))
  return hit?.path || '/admin/settings'
})
</script>

<template>
  <div class="admin-shell">
    <header class="topbar">
      <div class="brand" @click="router.push('/admin/settings')">
        <img class="brand-logo" src="/favicon.png" alt="" width="24" height="24" />
        <strong class="title">管理后台</strong>
      </div>
      <el-button plain @click="router.push('/')">退出管理</el-button>
    </header>
    <div class="body">
      <aside class="sidebar">
        <nav v-for="group in menuGroups" :key="group.label" class="nav-block">
          <div class="nav-group">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{ active: active === item.path }"
          >{{ item.label }}</router-link>
        </nav>
      </aside>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  min-width: 0;
  background: var(--omni-bg);
  color: var(--omni-text);
}
.topbar {
  height: 56px;
  background: var(--omni-card);
  border-bottom: 1px solid var(--omni-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--omni-space-3);
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  min-width: 0;
}
.title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  white-space: nowrap;
}
.brand-logo {
  width: 24px;
  height: 24px;
  object-fit: contain;
  display: block;
}
.body {
  display: flex;
  min-height: calc(100vh - 56px);
  min-width: 0;
}
.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--omni-surface);
  border-right: 1px solid var(--omni-border);
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-2);
}
.nav-block { display: flex; flex-direction: column; gap: 2px; }
.nav-group {
  font-size: 12px;
  color: var(--omni-muted);
  font-weight: 600;
  padding: 8px 10px 4px;
}
.nav-link {
  display: block;
  padding: 8px 10px;
  border-radius: var(--omni-radius-sm);
  color: var(--omni-text);
  text-decoration: none;
  font-size: 14px;
}
.nav-link:hover {
  background: var(--omni-accent-soft);
  color: var(--omni-accent-strong);
}
.nav-link.active {
  background: var(--omni-accent-soft);
  color: var(--omni-accent-strong);
  font-weight: 600;
}
.content {
  flex: 1;
  min-width: 0;
  background: var(--omni-bg);
}
</style>
