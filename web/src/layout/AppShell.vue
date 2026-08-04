<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { authApi, collectionApi, dashboardApi, settingsApi } from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { resourcePath, resourceTypeLabel } from '@/nav'
import { useUserStore } from '@/stores/user'
import type { Collection, CollectionItem, Id, ResourceType, SiteSettings } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const siteName = ref(t('shell.defaultSiteName'))
const collections = ref<Collection[]>([])
const treeKey = ref(0)
const defaultExpandedKeys = ref<string[]>([])
const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const createVisible = ref(false)
const createType = ref<'question' | 'sql' | 'dashboard' | 'model' | 'collection'>('question')
const createName = ref('')
const createCollectionId = ref<Id>()
const createSaving = ref(false)

type NavTreeNode = {
  id: string
  name: string
  label: string
  kind: 'COLLECTION' | ResourceType
  resourceId: Id
  personal?: boolean
  childCollections?: Collection[]
  isLeaf?: boolean
}

const flatCollections = computed(() => flattenCollections(collections.value))

const createTypeLabel = computed(() => {
  const labels: Record<typeof createType.value, string> = {
    question: t('shell.charts'),
    sql: t('shell.sqlQuery'),
    dashboard: t('shell.dashboards'),
    model: t('shell.models'),
    collection: t('common.collection'),
  }
  return labels[createType.value]
})

function flattenCollections(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flattenCollections(node.children, acc)
  }
  return acc
}

function toCollectionNode(collection: Collection): NavTreeNode {
  return {
    id: `collection:${collection.id}`,
    name: collection.name,
    label: collection.name,
    kind: 'COLLECTION',
    resourceId: collection.id,
    personal: collection.personalOwnerId != null,
    childCollections: collection.children || [],
  }
}

function toResourceNode(item: CollectionItem): NavTreeNode {
  return {
    id: `${item.type}:${item.id}`,
    name: item.name,
    label: item.name,
    kind: item.type,
    resourceId: item.id,
    isLeaf: true,
  }
}

async function loadTreeNode(
  node: { level: number; data: NavTreeNode },
  resolve: (data: NavTreeNode[]) => void,
) {
  if (node.level === 0) {
    resolve(collections.value.map(toCollectionNode))
    return
  }
  const data = node.data
  if (data.kind !== 'COLLECTION') {
    resolve([])
    return
  }
  try {
    const items = await collectionApi.items(data.resourceId)
    resolve([
      ...(data.childCollections || []).map(toCollectionNode),
      ...items.map(toResourceNode),
    ])
  } catch {
    resolve((data.childCollections || []).map(toCollectionNode))
  }
}

async function loadShell() {
  try {
    const [tree, settings] = await Promise.all([
      collectionApi.tree(),
      settingsApi.get().catch((): SiteSettings => ({})),
    ])
    collections.value = tree
    treeKey.value += 1
    defaultExpandedKeys.value = tree
      .filter((item) => item.personalOwnerId != null)
      .map((item) => `collection:${item.id}`)
    if (settings['site.name']) siteName.value = String(settings['site.name'])
    if (!createCollectionId.value) {
      const personal = flatCollections.value.find((item) => String(item.personalOwnerId) === String(userStore.user?.id))
        || flatCollections.value[0]
      if (personal) createCollectionId.value = personal.id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.navLoadFailed'))
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

function openPasswordDialog() {
  Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
  passwordVisible.value = true
}

async function changePassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) return ElMessage.warning(t('shell.fillPasswords'))
  if (passwordForm.newPassword.length < 10) return ElMessage.warning(t('shell.passwordMin'))
  if (passwordForm.currentPassword === passwordForm.newPassword) return ElMessage.warning(t('shell.passwordSame'))
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return ElMessage.warning(t('shell.passwordMismatch'))
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordVisible.value = false
    ElMessage.success(t('shell.passwordChanged'))
    logout()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.passwordFailed'))
  } finally {
    passwordSaving.value = false
  }
}

function goSearch() {
  const q = searchText.value.trim()
  router.push(q ? { path: '/search', query: { q } } : '/search')
}

function openCreate(type: typeof createType.value) {
  createType.value = type
  createName.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (createType.value !== 'question' && !createName.value.trim()) {
    return ElMessage.warning(t('shell.needName'))
  }
  if (!createCollectionId.value && createType.value !== 'collection') {
    return ElMessage.warning(t('shell.needCollection'))
  }
  createSaving.value = true
  try {
    const collectionId = createCollectionId.value
    if (createType.value === 'collection') {
      const created = await collectionApi.create({
        name: createName.value.trim(),
        parentId: collectionId,
      })
      createVisible.value = false
      await loadShell()
      await router.push(`/collections/${created.id}`)
      return
    }
    if (createType.value === 'question') {
      createVisible.value = false
      await router.push({ path: '/query', query: { collectionId: String(collectionId), intent: 'question' } })
      return
    }
    if (createType.value === 'sql') {
      createVisible.value = false
      await router.push({
        path: '/sql',
        query: {
          collectionId: String(collectionId),
          name: createName.value.trim(),
        },
      })
      return
    }
    if (createType.value === 'dashboard') {
      const created = await dashboardApi.create({
        name: createName.value.trim(),
        configJson: '{}',
        collectionId,
      })
      createVisible.value = false
      await loadShell()
      await router.push(`/dashboards/${created.id}/edit`)
      return
    }
    if (createType.value === 'model') {
      createVisible.value = false
      await router.push({ path: '/models', query: { create: '1', collectionId: String(collectionId) } })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.createFailed'))
  } finally {
    createSaving.value = false
  }
}

function onNavNodeClick(data: NavTreeNode) {
  if (data.kind === 'COLLECTION') {
    router.push(`/collections/${data.resourceId}`)
    return
  }
  router.push(resourcePath(data.kind, data.resourceId))
}

function onUserMenu(command: string) {
  if (command === 'admin') router.push('/admin')
  else if (command === 'trash') router.push('/trash')
  else if (command === 'password') openPasswordDialog()
  else if (command === 'logout') logout()
}

watch(() => route.query.q, (value) => {
  if (typeof value === 'string') searchText.value = value
})

onMounted(loadShell)
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand" @click="$router.push('/')">
        <img class="brand-logo" src="/favicon.png" alt="" width="28" height="28" />
        <span>{{ siteName }}</span>
      </div>
      <div class="search-wrap">
        <el-input
          v-model="searchText"
          clearable
          :placeholder="t('shell.searchPlaceholder')"
          @keyup.enter="goSearch"
        >
          <template #append>
            <el-button @click="goSearch">{{ t('common.search') }}</el-button>
          </template>
        </el-input>
      </div>
      <div class="top-actions">
        <ThemeSwitcher size="small" />
        <LanguageSwitcher size="small" />
        <el-dropdown trigger="click" @command="openCreate">
          <el-button type="primary">{{ t('shell.create') }}</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="question">{{ t('shell.charts') }}</el-dropdown-item>
              <el-dropdown-item v-if="userStore.hasPermission('query:raw')" command="sql">{{ t('shell.sqlQuery') }}</el-dropdown-item>
              <el-dropdown-item command="dashboard">{{ t('shell.dashboards') }}</el-dropdown-item>
              <el-dropdown-item command="model">{{ t('shell.models') }}</el-dropdown-item>
              <el-dropdown-item command="collection">{{ t('common.collection') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <div class="body">
      <aside class="sidebar">
        <nav class="nav-block">
          <router-link class="nav-link" to="/" :class="{ active: route.path === '/' }">{{ t('shell.home') }}</router-link>
          <router-link
            class="nav-link"
            to="/dashboards"
            :class="{ active: route.path === '/dashboards' || route.path.startsWith('/dashboards/') }"
          >{{ t('shell.dashboards') }}</router-link>
        </nav>
        <div class="nav-block">
          <div class="nav-group">{{ t('shell.collections') }}</div>
          <el-tree
            :key="treeKey"
            lazy
            :load="loadTreeNode"
            node-key="id"
            :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
            :default-expanded-keys="defaultExpandedKeys"
            highlight-current
            :expand-on-click-node="false"
            @node-click="onNavNodeClick"
          >
            <template #default="{ data }">
              <span
                class="tree-node"
                :class="{ personal: data.personal, resource: data.kind !== 'COLLECTION' }"
              >
                <span v-if="data.kind !== 'COLLECTION'" class="node-type">{{ resourceTypeLabel(data.kind) }}</span>
                {{ data.name }}
              </span>
            </template>
          </el-tree>
        </div>
        <div class="nav-block">
          <div class="nav-group">{{ t('shell.data') }}</div>
          <router-link class="nav-link" to="/databases" :class="{ active: route.path.startsWith('/databases') }">{{ t('shell.dataSources') }}</router-link>
          <router-link
            v-if="userStore.hasPermission('query:raw')"
            class="nav-link"
            to="/sql"
            :class="{ active: route.path.startsWith('/sql') }"
          >{{ t('shell.sqlQuery') }}</router-link>
          <router-link class="nav-link" to="/models" :class="{ active: route.path.startsWith('/models') }">{{ t('shell.models') }}</router-link>
          <router-link class="nav-link" to="/metrics" :class="{ active: route.path.startsWith('/metrics') }">{{ t('shell.metrics') }}</router-link>
        </div>
        <div class="nav-footer">
          <div class="user-row">
            <div class="user-info">
              <span class="user-avatar">{{ (userStore.user?.displayName || userStore.user?.username || '?').slice(0, 1) }}</span>
              <span class="user-meta">
                <strong>{{ userStore.user?.displayName || userStore.user?.username }}</strong>
                <small>{{ userStore.user?.username }}</small>
              </span>
            </div>
            <el-dropdown trigger="click" placement="top-end" @command="onUserMenu">
              <button type="button" class="more-btn" :title="t('common.more')" :aria-label="t('shell.moreMenu')">⋯</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="userStore.isAdmin" command="admin">{{ t('shell.admin') }}</el-dropdown-item>
                  <el-dropdown-item command="trash">{{ t('shell.trash') }}</el-dropdown-item>
                  <el-dropdown-item divided command="password">{{ t('shell.changePassword') }}</el-dropdown-item>
                  <el-dropdown-item command="logout">{{ t('shell.logout') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </aside>
      <main class="content">
        <router-view />
      </main>
    </div>

    <el-dialog v-model="createVisible" :title="t('shell.createTitle', { type: createTypeLabel })" width="460px">
      <el-form label-width="90px">
        <el-form-item v-if="createType !== 'question'" :label="t('common.name')">
          <el-input v-model="createName" :placeholder="t('shell.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="createType === 'collection' ? t('shell.parentCollection') : t('shell.belongCollection')">
          <el-select v-model="createCollectionId" class="full-width" :clearable="createType === 'collection'" :placeholder="t('shell.selectCollection')">
            <el-option v-for="item in flatCollections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <p v-if="createType === 'question'" class="hint">{{ t('shell.hintQuestion') }}</p>
        <p v-if="createType === 'sql'" class="hint">{{ t('shell.hintSql') }}</p>
        <p v-if="createType === 'model'" class="hint">{{ t('shell.hintModel') }}</p>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">{{ t('common.continue') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" :title="t('shell.changePassword')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="t('shell.currentPassword')">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item :label="t('shell.newPassword')">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="t('shell.confirmPassword')">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="changePassword">{{ t('shell.confirmChange') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  min-width: 0;
  background: var(--omni-bg);
  color: var(--omni-text);
}
.topbar {
  height: 56px;
  background: var(--omni-card);
  border-bottom: 1px solid var(--omni-border);
  display: grid;
  grid-template-columns: 220px minmax(240px, 560px) auto;
  align-items: center;
  gap: var(--omni-space-3);
  padding: 0 var(--omni-space-3);
}
.brand {
  display: flex;
  align-items: center;
  gap: var(--omni-space-2);
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  min-width: 0;
  letter-spacing: -0.01em;
}
.brand-logo {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  object-fit: contain;
  display: block;
}
.brand span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.search-wrap { width: 100%; min-width: 0; }
.top-actions {
  display: flex;
  align-items: center;
  gap: var(--omni-space-2);
  justify-content: flex-end;
  padding-right: 2px;
}
.body { display: flex; min-height: calc(100vh - 56px); min-width: 0; }
.sidebar {
  width: 240px;
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
.nav-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--omni-border);
}
.user-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 4px 4px 8px;
  border-radius: var(--omni-radius-sm);
}
.user-info {
  display: flex;
  align-items: center;
  gap: var(--omni-space-2);
  min-width: 0;
  flex: 1;
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--omni-accent);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}
.user-meta strong {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-meta small {
  color: var(--omni-muted);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.more-btn {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--omni-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
}
.more-btn:hover {
  background: var(--omni-accent-soft);
  color: var(--omni-text);
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
}
.tree-node.personal { font-weight: 600; color: var(--omni-accent-strong); }
.tree-node.resource { font-weight: 400; color: var(--omni-text); }
.node-type {
  flex: 0 0 auto;
  font-size: 11px;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 4px;
  color: var(--omni-muted);
  background: var(--omni-bg);
  border: 1px solid var(--omni-border);
}
.content { flex: 1; min-width: 0; background: var(--omni-bg); }
.hint { margin: 0; color: var(--omni-muted); font-size: 13px; }
:deep(.el-tree) { background: transparent; }
:deep(.el-tree-node__content) { border-radius: var(--omni-radius-sm); height: 32px; }
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--omni-accent-soft);
  color: var(--omni-accent-strong);
}
</style>
