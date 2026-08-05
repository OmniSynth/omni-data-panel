<script setup lang="ts">
import { computed, nextTick, onMounted, provide, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { authApi, collectionApi, dashboardApi, settingsApi } from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { minLengthRule, requiredRule, validateForm } from '@/form/rules'
import { resourcePath, resourceTypeLabel } from '@/nav'
import { refreshShellNavKey } from '@/nav/shellNav'
import { useUserStore } from '@/stores/user'
import type { Collection, CollectionItem, Id, ResourceType, SiteSettings } from '@/types'

const SIDEBAR_COLLAPSED_KEY = 'omni.sidebarCollapsed'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const siteName = ref(t('shell.defaultSiteName'))
const collections = ref<Collection[]>([])
const treeKey = ref(0)
const defaultExpandedKeys = ref<string[]>([])
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1')
const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const mfaVisible = ref(false)
const mfaSaving = ref(false)
const mfaEnabled = ref(false)
const mfaPhase = ref<'status' | 'setup' | 'backup' | 'disable'>('status')
const mfaSecret = ref('')
const mfaOtpauthUri = ref('')
const mfaQrDataUrl = ref('')
const mfaBackupCodes = ref<string[]>([])
const mfaConfirmFormRef = ref<FormInstance>()
const mfaDisableFormRef = ref<FormInstance>()
const mfaConfirmForm = reactive({ code: '' })
const mfaDisableForm = reactive({ password: '', code: '' })

const createVisible = ref(false)
const createType = ref<'question' | 'sql' | 'dashboard' | 'model' | 'collection'>('question')
const createFormRef = ref<FormInstance>()
const createForm = reactive<{ name: string; collectionId?: Id }>({ name: '', collectionId: undefined })
const createSaving = ref(false)

const createRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  if (createType.value !== 'question') {
    rules.name = [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))]
  }
  if (createType.value !== 'collection') {
    rules.collectionId = [requiredRule(t('common.pleaseSelect', { field: t('shell.belongCollection') }), 'change')]
  }
  return rules
})

const passwordRules = computed<FormRules>(() => ({
  currentPassword: [requiredRule(t('common.pleaseEnter', { field: t('shell.currentPassword') }))],
  newPassword: [
    requiredRule(t('common.pleaseEnter', { field: t('shell.newPassword') })),
    minLengthRule(10, t('shell.passwordMin')),
    {
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        if (value && value === passwordForm.currentPassword) callback(new Error(t('shell.passwordSame')))
        else callback()
      },
    },
  ],
  confirmPassword: [
    requiredRule(t('common.pleaseEnter', { field: t('shell.confirmPassword') })),
    {
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error(t('shell.passwordMismatch')))
        else callback()
      },
    },
  ],
}))

const mfaConfirmRules = computed<FormRules>(() => ({
  code: [requiredRule(t('common.pleaseEnter', { field: t('shell.mfaCode') }))],
}))

const mfaDisableRules = computed<FormRules>(() => ({
  password: [requiredRule(t('common.pleaseEnter', { field: t('shell.currentPassword') }))],
  code: [requiredRule(t('common.pleaseEnter', { field: t('shell.mfaCode') }))],
}))

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
    const myUserId = userStore.user?.id
    defaultExpandedKeys.value = tree
      .filter((item) => item.personalOwnerId != null && String(item.personalOwnerId) === String(myUserId))
      .map((item) => `collection:${item.id}`)
    if (settings['site.name']) siteName.value = String(settings['site.name'])
    if (!createForm.collectionId) {
      createForm.collectionId = createCollectionIdDefault()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.navLoadFailed'))
  }
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed.value ? '1' : '0')
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
  if (!(await validateForm(passwordFormRef.value))) return
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

async function openMfaDialog() {
  mfaPhase.value = 'status'
  mfaSecret.value = ''
  mfaOtpauthUri.value = ''
  mfaQrDataUrl.value = ''
  mfaBackupCodes.value = []
  mfaConfirmForm.code = ''
  Object.assign(mfaDisableForm, { password: '', code: '' })
  mfaVisible.value = true
  mfaSaving.value = true
  try {
    const status = await authApi.mfaStatus()
    mfaEnabled.value = status.enabled
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.mfaLoadFailed'))
    mfaVisible.value = false
  } finally {
    mfaSaving.value = false
  }
}

async function startMfaSetup() {
  mfaSaving.value = true
  try {
    const setup = await authApi.beginMfaSetup()
    mfaSecret.value = setup.secret
    mfaOtpauthUri.value = setup.otpauthUri
    mfaQrDataUrl.value = await QRCode.toDataURL(setup.otpauthUri, { width: 200, margin: 1 })
    mfaConfirmForm.code = ''
    mfaPhase.value = 'setup'
    await nextTick()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.mfaSetupFailed'))
  } finally {
    mfaSaving.value = false
  }
}

async function confirmMfaSetup() {
  if (!(await validateForm(mfaConfirmFormRef.value))) return
  mfaSaving.value = true
  try {
    const result = await authApi.confirmMfa(mfaConfirmForm.code.trim())
    mfaBackupCodes.value = result.backupCodes
    mfaEnabled.value = true
    mfaPhase.value = 'backup'
    ElMessage.success(t('shell.mfaEnabled'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.mfaConfirmFailed'))
  } finally {
    mfaSaving.value = false
  }
}

async function copyBackupCodes() {
  try {
    await navigator.clipboard.writeText(mfaBackupCodes.value.join('\n'))
    ElMessage.success(t('shell.mfaBackupCopied'))
  } catch {
    ElMessage.error(t('shell.mfaBackupCopyFailed'))
  }
}

function openMfaDisable() {
  Object.assign(mfaDisableForm, { password: '', code: '' })
  mfaPhase.value = 'disable'
}

async function disableMfa() {
  if (!(await validateForm(mfaDisableFormRef.value))) return
  mfaSaving.value = true
  try {
    await authApi.disableMfa({
      password: mfaDisableForm.password,
      code: mfaDisableForm.code.trim(),
    })
    mfaEnabled.value = false
    mfaPhase.value = 'status'
    ElMessage.success(t('shell.mfaDisabled'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('shell.mfaDisableFailed'))
  } finally {
    mfaSaving.value = false
  }
}

function goSearch() {
  const q = searchText.value.trim()
  router.push(q ? { path: '/search', query: { q } } : '/search')
}

function openCreate(type: typeof createType.value) {
  createType.value = type
  createForm.name = ''
  createForm.collectionId = createCollectionIdDefault()
  createVisible.value = true
}

function createCollectionIdDefault(): Id | undefined {
  const myUserId = userStore.user?.id
  const mine = collections.value.find(
    (item) => item.personalOwnerId != null && String(item.personalOwnerId) === String(myUserId),
  )
  return mine?.id
    ?? collections.value.find((item) => item.personalOwnerId != null)?.id
    ?? collections.value[0]?.id
}

async function submitCreate() {
  if (!(await validateForm(createFormRef.value))) return
  createSaving.value = true
  try {
    const collectionId = createForm.collectionId
    if (createType.value === 'collection') {
      const created = await collectionApi.create({
        name: createForm.name.trim(),
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
          name: createForm.name.trim(),
        },
      })
      return
    }
    if (createType.value === 'dashboard') {
      const created = await dashboardApi.create({
        name: createForm.name.trim(),
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
  else if (command === 'mfa') void openMfaDialog()
  else if (command === 'logout') logout()
}

watch(() => route.query.q, (value) => {
  if (typeof value === 'string') searchText.value = value
})

provide(refreshShellNavKey, loadShell)
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
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-toolbar">
          <el-tooltip
            :content="sidebarCollapsed ? t('shell.expandSidebar') : t('shell.collapseSidebar')"
            placement="right"
            :show-after="400"
          >
            <button
              type="button"
              class="sidebar-toggle"
              :aria-label="sidebarCollapsed ? t('shell.expandSidebar') : t('shell.collapseSidebar')"
              :aria-expanded="!sidebarCollapsed"
              @click="toggleSidebar"
            >
              <svg class="sidebar-toggle-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  v-if="sidebarCollapsed"
                  fill="currentColor"
                  d="M9.7 6.3a1 1 0 0 1 1.4 0l5 5a1 1 0 0 1 0 1.4l-5 5a1 1 0 1 1-1.4-1.4L13.58 12 9.7 8.12a1 1 0 0 1 0-1.42z"
                />
                <path
                  v-else
                  fill="currentColor"
                  d="M14.3 6.3a1 1 0 0 1 0 1.4L10.42 12l3.88 3.88a1 1 0 1 1-1.4 1.4l-5-5a1 1 0 0 1 0-1.4l5-5a1 1 0 0 1 1.4 0z"
                />
              </svg>
            </button>
          </el-tooltip>
        </div>
        <div class="sidebar-content">
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
                  <el-dropdown-item command="mfa">{{ t('shell.mfa') }}</el-dropdown-item>
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

    <el-dialog v-model="createVisible" :title="t('shell.createTitle', { type: createTypeLabel })" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item v-if="createType !== 'question'" :label="t('common.name')" prop="name">
          <el-input v-model="createForm.name" :placeholder="t('shell.namePlaceholder')" />
        </el-form-item>
        <el-form-item
          :label="createType === 'collection' ? t('shell.parentCollection') : t('shell.belongCollection')"
          prop="collectionId"
        >
          <el-select v-model="createForm.collectionId" class="full-width" :clearable="createType === 'collection'" :placeholder="t('shell.selectCollection')">
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

    <el-dialog v-model="passwordVisible" :title="t('shell.changePassword')" width="420px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item :label="t('shell.currentPassword')" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item :label="t('shell.newPassword')" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="t('shell.confirmPassword')" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="changePassword">{{ t('shell.confirmChange') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mfaVisible" :title="t('shell.mfa')" width="480px" destroy-on-close>
      <div v-loading="mfaSaving && mfaPhase === 'status'">
        <template v-if="mfaPhase === 'status'">
          <p class="mfa-status">
            {{ mfaEnabled ? t('shell.mfaEnabledHint') : t('shell.mfaDisabledHint') }}
          </p>
          <el-button v-if="!mfaEnabled" type="primary" :loading="mfaSaving" @click="startMfaSetup">
            {{ t('shell.mfaStart') }}
          </el-button>
          <el-button v-else type="danger" plain @click="openMfaDisable">{{ t('shell.mfaDisable') }}</el-button>
        </template>
        <template v-else-if="mfaPhase === 'setup'">
          <p class="hint">{{ t('shell.mfaSetupHint') }}</p>
          <div class="mfa-qr-wrap">
            <img v-if="mfaQrDataUrl" class="mfa-qr" :src="mfaQrDataUrl" alt="TOTP QR" />
          </div>
          <p class="mfa-secret">{{ t('shell.mfaSecret') }}：<code>{{ mfaSecret }}</code></p>
          <el-form ref="mfaConfirmFormRef" :model="mfaConfirmForm" :rules="mfaConfirmRules" label-width="90px">
            <el-form-item :label="t('shell.mfaCode')" prop="code">
              <el-input v-model="mfaConfirmForm.code" autocomplete="one-time-code" />
            </el-form-item>
          </el-form>
        </template>
        <template v-else-if="mfaPhase === 'backup'">
          <el-alert type="warning" :closable="false" show-icon :title="t('shell.mfaBackupHint')" />
          <ul class="mfa-backup-list">
            <li v-for="code in mfaBackupCodes" :key="code"><code>{{ code }}</code></li>
          </ul>
          <el-button @click="copyBackupCodes">{{ t('shell.mfaCopyBackup') }}</el-button>
        </template>
        <template v-else>
          <p class="hint">{{ t('shell.mfaDisableHint') }}</p>
          <el-form ref="mfaDisableFormRef" :model="mfaDisableForm" :rules="mfaDisableRules" label-width="90px">
            <el-form-item :label="t('shell.currentPassword')" prop="password">
              <el-input v-model="mfaDisableForm.password" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item :label="t('shell.mfaCode')" prop="code">
              <el-input v-model="mfaDisableForm.code" autocomplete="one-time-code" :placeholder="t('shell.mfaCodeOrBackup')" />
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="mfaVisible = false">{{ t('common.close') }}</el-button>
        <el-button
          v-if="mfaPhase === 'setup'"
          type="primary"
          :loading="mfaSaving"
          @click="confirmMfaSetup"
        >
          {{ t('shell.mfaConfirm') }}
        </el-button>
        <el-button
          v-if="mfaPhase === 'disable'"
          type="danger"
          :loading="mfaSaving"
          @click="disableMfa"
        >
          {{ t('shell.mfaDisableConfirm') }}
        </el-button>
        <el-button v-if="mfaPhase === 'backup'" type="primary" @click="mfaVisible = false">
          {{ t('shell.mfaBackupDone') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--omni-bg);
  color: var(--omni-text);
}
.mfa-status { margin: 0 0 16px; color: var(--omni-text); line-height: 1.6; }
.mfa-qr-wrap { display: flex; justify-content: center; margin: 12px 0; }
.mfa-qr { width: 200px; height: 200px; border-radius: 8px; background: #fff; }
.mfa-secret { font-size: 13px; word-break: break-all; color: var(--omni-muted); }
.mfa-backup-list {
  margin: 12px 0;
  padding: 12px 16px;
  list-style: none;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  background: var(--omni-bg);
  border-radius: 8px;
}
.hint { margin: 0 0 12px; color: var(--omni-muted); font-size: 13px; line-height: 1.5; }
.topbar {
  height: 56px;
  flex-shrink: 0;
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
.body {
  display: flex;
  align-items: stretch;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
.sidebar {
  width: 240px;
  flex: 0 0 auto;
  height: 100%;
  min-height: 0;
  background: var(--omni-surface);
  border-right: 1px solid var(--omni-border);
  padding: 8px 10px 12px;
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-2);
  overflow: hidden;
  transition: width 0.2s ease, padding 0.2s ease;
}
.sidebar.collapsed {
  width: 44px;
  padding: 8px 6px;
}
.sidebar-toolbar {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}
.sidebar.collapsed .sidebar-toolbar {
  justify-content: center;
}
.sidebar-toggle {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--omni-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
}
.sidebar-toggle:hover {
  background: var(--omni-accent-soft);
  color: var(--omni-text);
}
.sidebar-toggle-icon {
  width: 18px;
  height: 18px;
  display: block;
}
.sidebar-content {
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-2);
  flex: 1 1 auto;
  min-height: 0;
  min-width: 220px;
  overflow-x: hidden;
  overflow-y: auto;
  opacity: 1;
  transition: opacity 0.15s ease;
}
.sidebar.collapsed .sidebar-content {
  opacity: 0;
  pointer-events: none;
  visibility: hidden;
  width: 0;
  min-width: 0;
  overflow: hidden;
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
  flex-shrink: 0;
  padding-top: 12px;
  border-top: 1px solid var(--omni-border);
  background: var(--omni-surface);
}
.sidebar.collapsed .nav-footer {
  opacity: 0;
  pointer-events: none;
  visibility: hidden;
  height: 0;
  padding: 0;
  border: 0;
  overflow: hidden;
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
.content {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: var(--omni-bg);
}
.hint { margin: 0; color: var(--omni-muted); font-size: 13px; }
:deep(.el-tree) { background: transparent; }
:deep(.el-tree-node__content) { border-radius: var(--omni-radius-sm); height: 32px; }
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--omni-accent-soft);
  color: var(--omni-accent-strong);
}
</style>
