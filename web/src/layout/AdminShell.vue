<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const menuGroups = computed(() => [
  {
    label: t('adminShell.system'),
    items: [
      { path: '/admin/settings', label: t('adminShell.settings') },
      { path: '/admin/users', label: t('adminShell.users') },
      { path: '/admin/roles', label: t('adminShell.roles') },
      { path: '/admin/subscriptions', label: t('adminShell.subscriptions') },
      { path: '/admin/schedules', label: t('adminShell.schedules') },
    ],
  },
  {
    label: t('adminShell.dataSources'),
    items: [
      { path: '/admin/databases', label: t('adminShell.dataSources') },
      { path: '/admin/data-source-health', label: t('adminShell.poolHealth') },
    ],
  },
  {
    label: t('adminShell.audit'),
    items: [
      { path: '/admin/query-audits', label: t('adminShell.queryAudits') },
      { path: '/admin/login-audits', label: t('adminShell.loginAudits') },
      { path: '/admin/system-logs', label: t('adminShell.systemLogs') },
      { path: '/admin/dataset-audits', label: t('adminShell.datasetAudits') },
    ],
  },
])

const flatMenus = computed(() => menuGroups.value.flatMap((group) => group.items))

const active = computed(() => {
  const hit = flatMenus.value.find((item) => route.path.startsWith(item.path))
  return hit?.path || '/admin/settings'
})
</script>

<template>
  <div class="admin-shell">
    <header class="topbar">
      <div class="brand" @click="router.push('/admin/settings')">
        <img class="brand-logo" src="/favicon.png" alt="" width="24" height="24" />
        <strong class="title">{{ t('adminShell.title') }}</strong>
      </div>
      <div class="top-actions">
        <ThemeSwitcher size="small" />
        <LanguageSwitcher size="small" />
        <el-button plain @click="router.push('/')">{{ t('adminShell.exit') }}</el-button>
      </div>
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
.top-actions {
  display: flex;
  align-items: center;
  gap: var(--omni-space-2);
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
