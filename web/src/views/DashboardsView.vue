<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox, promptBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { dashboardApi, publicLinkApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Dashboard, Id, PublicLink } from '@/types'
import PublicShareDialog from '@/components/PublicShareDialog.vue'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'
import { copyText } from '@/utils/clipboard'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const rows = ref<Dashboard[]>([])
const cardCounts = ref<Record<string, number>>({})
const loading = ref(false)
const linksLoading = ref(false)
const creatingLink = ref(false)
const linksVisible = ref(false)
const linksDashboard = ref<Dashboard>()
const links = ref<PublicLink[]>([])
const permissionVisible = ref(false)
const permissionDashboard = ref<Dashboard>()

const totalCharts = computed(() =>
  rows.value.reduce((sum, item) => sum + (cardCounts.value[String(item.id)] || 0), 0))

function publicUrl(token: string) {
  return `${location.origin}/public/dashboard/${token}`
}

function accessTagType(level: string) {
  if (level === 'ADMIN' || level === 'OWNER') return 'success'
  if (level === 'WRITE') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    rows.value = await dashboardApi.list()
    const entries = await Promise.all(rows.value.map(async (dashboard) =>
      [String(dashboard.id), (await dashboardApi.cards(dashboard.id)).length] as const))
    cardCounts.value = Object.fromEntries(entries)
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : t('dashboard.loadFailed')) }
  finally { loading.value = false }
}

async function create() {
  try {
    const { value } = await promptBox(t('dashboard.namePrompt'), t('dashboard.createTitle'), {
      inputPattern: /\S+/,
      inputErrorMessage: t('common.nameRequired'),
    })
    const dashboard = await dashboardApi.create({ name: value, configJson: '{}' })
    await router.push(`/dashboards/${dashboard.id}/edit`)
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.createFailed')) }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('dashboard.deleteConfirm'), t('common.deleteConfirmTitle'))
    await dashboardApi.remove(id)
    await load()
  }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed')) }
}

async function loadLinks() {
  if (!linksDashboard.value) return
  linksLoading.value = true
  try {
    const all = await publicLinkApi.list({
      resourceType: 'DASHBOARD',
      resourceId: linksDashboard.value.id,
    })
    const dashboardId = String(linksDashboard.value.id)
    links.value = all.filter((link) =>
      link.enabled !== false
      && link.resourceType === 'DASHBOARD'
      && String(link.resourceId) === dashboardId)
  } catch {
    links.value = []
  } finally {
    linksLoading.value = false
  }
}

async function openPublicLinks(dashboard: Dashboard) {
  linksDashboard.value = dashboard
  linksVisible.value = true
  await loadLinks()
}

async function createPublicLink() {
  if (!linksDashboard.value || creatingLink.value) return
  creatingLink.value = true
  try {
    const link = await publicLinkApi.create({
      resourceType: 'DASHBOARD',
      resourceId: linksDashboard.value.id,
    })
    links.value = [link, ...links.value.filter((item) => item.token !== link.token)]
    const url = publicUrl(link.token)
    if (await copyText(url)) {
      ElMessage.success(t('dashboard.linkCopied'))
    } else {
      ElMessage.success(t('dashboard.linkCreated'))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.linkCreateFailed'))
  } finally {
    creatingLink.value = false
  }
}

async function copyLink(link: PublicLink) {
  if (await copyText(publicUrl(link.token))) {
    ElMessage.success(t('dashboard.linkCopied'))
  } else {
    ElMessage.error(t('dashboard.linkCopyFailed'))
  }
}

async function revokeLink(link: PublicLink) {
  const previous = links.value
  links.value = links.value.filter((item) => item.token !== link.token)
  try {
    await publicLinkApi.revoke(link.id)
    ElMessage.success(t('dashboard.revoked'))
  } catch (error) {
    links.value = previous
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.revokeFailed'))
  }
}

function canEdit(dashboard: Dashboard) {
  return ['ADMIN', 'OWNER', 'WRITE'].includes(dashboard.accessLevel)
}

function canDelete(dashboard: Dashboard) {
  return ['ADMIN', 'OWNER', 'WRITE'].includes(dashboard.accessLevel)
}

function canRoleShare(dashboard: Dashboard) {
  return userStore.isAdmin || ['ADMIN', 'OWNER'].includes(dashboard.accessLevel)
}

function authorize(dashboard: Dashboard) {
  permissionDashboard.value = dashboard
  permissionVisible.value = true
}

function openView(dashboard: Dashboard) {
  void router.push(`/dashboards/${dashboard.id}/view`)
}

function onMoreCommand(command: string, dashboard: Dashboard) {
  if (command === 'edit') void router.push(`/dashboards/${dashboard.id}/edit`)
  else if (command === 'public') void openPublicLinks(dashboard)
  else if (command === 'roles') authorize(dashboard)
  else if (command === 'delete') void remove(dashboard.id)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page dashboards-page">
    <div class="hero">
      <div class="hero-copy">
        <h1 class="page-title">{{ t('dashboard.title') }}</h1>
        <p class="hero-desc">{{ t('dashboard.subtitle') }}</p>
        <div v-if="rows.length" class="hero-stats">
          <span class="stat">
            <strong>{{ rows.length }}</strong>
            {{ t('dashboard.title') }}
          </span>
          <span class="stat-dot" aria-hidden="true" />
          <span class="stat">
            <strong>{{ totalCharts }}</strong>
            {{ t('dashboard.chartCount') }}
          </span>
        </div>
      </div>
      <el-button
        v-if="userStore.hasPermission('dashboard:create')"
        type="primary"
        size="large"
        class="create-btn"
        @click="create"
      >
        {{ t('dashboard.create') }}
      </el-button>
    </div>

    <div v-if="!loading && !rows.length" class="empty-panel">
      <div class="empty-visual" aria-hidden="true">
        <svg viewBox="0 0 64 64" width="56" height="56" fill="none">
          <rect x="8" y="12" width="48" height="40" rx="8" stroke="currentColor" stroke-width="2.2" />
          <path d="M16 36h10M16 28h20M34 36h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
          <circle cx="44" cy="24" r="4" fill="currentColor" opacity="0.35" />
        </svg>
      </div>
      <strong>{{ t('dashboard.empty') }}</strong>
      <span>{{ t('dashboard.emptyHint') }}</span>
      <el-button
        v-if="userStore.hasPermission('dashboard:create')"
        type="primary"
        @click="create"
      >
        {{ t('dashboard.create') }}
      </el-button>
    </div>

    <div v-else class="dash-grid">
      <article
        v-for="row in rows"
        :key="row.id"
        class="dash-card"
        @click="openView(row)"
      >
        <div class="dash-thumb" aria-hidden="true">
          <div class="thumb-bars">
            <span style="height:42%" />
            <span style="height:68%" />
            <span style="height:54%" />
            <span style="height:78%" />
            <span style="height:46%" />
          </div>
          <div class="thumb-glow" />
        </div>
        <div class="dash-body">
          <div class="dash-top">
            <h2 class="dash-name" :title="row.name">{{ row.name }}</h2>
            <el-tag size="small" :type="accessTagType(row.accessLevel)" effect="plain">
              {{ displayLabel(row.accessLevel) }}
            </el-tag>
          </div>
          <p class="dash-meta">
            {{ t('dashboard.chartCountLabel', { n: cardCounts[String(row.id)] || 0 }) }}
            <template v-if="row.description"> · {{ row.description }}</template>
          </p>
          <div class="dash-actions" @click.stop>
            <el-button type="primary" plain size="small" @click="openView(row)">
              {{ t('dashboard.openDashboard') }}
            </el-button>
            <el-dropdown
              v-if="canEdit(row) || canRoleShare(row) || canDelete(row)"
              trigger="click"
              @command="(cmd: string) => onMoreCommand(cmd, row)"
            >
              <el-button size="small" text>
                {{ t('dashboard.moreActions') }}
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canEdit(row)" command="edit">{{ t('common.edit') }}</el-dropdown-item>
                  <el-dropdown-item v-if="canEdit(row)" command="public">{{ t('dashboard.publicShare') }}</el-dropdown-item>
                  <el-dropdown-item v-if="canRoleShare(row)" command="roles">{{ t('dashboard.roleShare') }}</el-dropdown-item>
                  <el-dropdown-item v-if="canDelete(row)" command="delete" divided>
                    <span class="danger-text">{{ t('common.delete') }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </article>
    </div>

    <PublicShareDialog
      v-model="linksVisible"
      :resource-name="linksDashboard?.name"
      :hint="t('dashboard.publicLinksHint')"
      :empty-text="t('dashboard.publicLinksEmpty')"
      :links="links"
      :loading="linksLoading"
      :creating="creatingLink"
      :url-for="(link) => publicUrl(link.token)"
      @create="createPublicLink"
      @copy="copyLink"
      @revoke="revokeLink"
    />

    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="DASHBOARD"
      :resource-id="permissionDashboard?.id"
      :allowed-permissions="['READ', 'WRITE']"
      :title="`${t('dashboard.roleShareTitle')}${permissionDashboard?.name || ''}`"
      :hint="t('roleGrant.resourceHint')"
    />
  </div>
</template>

<style scoped>
.dashboards-page {
  padding: 28px 32px;
}
.hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
  padding: 22px 24px;
  border-radius: 14px;
  border: 1px solid var(--omni-border);
  background:
    radial-gradient(720px 180px at 0% 0%, color-mix(in srgb, var(--omni-accent) 18%, transparent), transparent 60%),
    var(--omni-card);
  box-shadow: var(--omni-shadow);
}
.hero-copy { min-width: 0; }
.hero .page-title { margin-bottom: 6px; }
.hero-desc {
  margin: 0;
  color: var(--omni-muted);
  font-size: 13px;
  line-height: 1.5;
}
.hero-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  color: var(--omni-muted);
  font-size: 13px;
}
.stat strong {
  color: var(--omni-text);
  margin-right: 4px;
  font-size: 16px;
}
.stat-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--omni-border);
}
.create-btn { flex-shrink: 0; }

.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 320px;
  padding: 40px 24px;
  border: 1px dashed var(--omni-border);
  border-radius: 14px;
  background: var(--omni-surface);
  text-align: center;
}
.empty-visual {
  color: var(--omni-accent);
  margin-bottom: 8px;
}
.empty-panel strong {
  font-size: 16px;
  color: var(--omni-text);
}
.empty-panel span {
  max-width: 360px;
  color: var(--omni-muted);
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 8px;
}

.dash-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.dash-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--omni-border);
  border-radius: 14px;
  background: var(--omni-card);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.dash-card:hover {
  border-color: color-mix(in srgb, var(--omni-accent) 45%, var(--omni-border));
  box-shadow: var(--omni-shadow);
  transform: translateY(-2px);
}
.dash-thumb {
  position: relative;
  height: 112px;
  background:
    linear-gradient(145deg, color-mix(in srgb, var(--omni-accent) 14%, var(--omni-surface)), var(--omni-surface));
  border-bottom: 1px solid var(--omni-border);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 18px 28px 16px;
  overflow: hidden;
}
.thumb-bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
  height: 100%;
  z-index: 1;
}
.thumb-bars span {
  flex: 1;
  border-radius: 6px 6px 2px 2px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--omni-accent) 75%, white), var(--omni-accent));
  opacity: 0.85;
}
.thumb-glow {
  position: absolute;
  inset: auto -20% -40% 40%;
  height: 90px;
  background: radial-gradient(circle, color-mix(in srgb, var(--omni-accent) 35%, transparent), transparent 70%);
  pointer-events: none;
}
.dash-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 16px 14px;
  min-height: 132px;
}
.dash-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.dash-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--omni-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.dash-meta {
  margin: 0;
  font-size: 12px;
  color: var(--omni-muted);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.dash-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}
.danger-text { color: var(--omni-danger); }

@media (max-width: 720px) {
  .dashboards-page { padding: 20px; }
  .hero {
    flex-direction: column;
    align-items: stretch;
  }
  .create-btn { width: 100%; }
}
</style>
