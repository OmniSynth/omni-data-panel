<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { authApi, collectionApi, dashboardApi, settingsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { Collection, Id, SiteSettings } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const siteName = ref('全域数据分析')
const collections = ref<Collection[]>([])
const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const createVisible = ref(false)
const createType = ref<'question' | 'sql' | 'dashboard' | 'model' | 'collection'>('question')
const createName = ref('')
const createCollectionId = ref<Id>()
const createSaving = ref(false)

const flatCollections = computed(() => flattenCollections(collections.value))
const personalIds = computed(() => new Set(
  flatCollections.value.filter((item) => item.personalOwnerId != null).map((item) => String(item.id)),
))

function flattenCollections(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flattenCollections(node.children, acc)
  }
  return acc
}

function toTree(nodes: Collection[]): Array<Collection & { label: string }> {
  return nodes.map((node) => ({
    ...node,
    label: node.name,
    children: node.children ? toTree(node.children) : undefined,
  }))
}

const collectionTree = computed(() => toTree(collections.value))

async function loadShell() {
  try {
    const [tree, settings] = await Promise.all([
      collectionApi.tree(),
      settingsApi.get().catch((): SiteSettings => ({})),
    ])
    collections.value = tree
    if (settings['site.name']) siteName.value = String(settings['site.name'])
    if (!createCollectionId.value) {
      const personal = flatCollections.value.find((item) => String(item.personalOwnerId) === String(userStore.user?.id))
        || flatCollections.value[0]
      if (personal) createCollectionId.value = personal.id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导航加载失败')
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
  if (!passwordForm.currentPassword || !passwordForm.newPassword) return ElMessage.warning('请填写当前密码和新密码')
  if (passwordForm.newPassword.length < 10) return ElMessage.warning('新密码至少需要10位')
  if (passwordForm.currentPassword === passwordForm.newPassword) return ElMessage.warning('新密码不能与当前密码相同')
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordVisible.value = false
    ElMessage.success('密码修改成功，请重新登录')
    logout()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码修改失败')
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
    return ElMessage.warning('请输入名称')
  }
  if (!createCollectionId.value && createType.value !== 'collection') {
    return ElMessage.warning('请选择集合')
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
      await router.push(`/dashboards/${created.id}/edit`)
      return
    }
    if (createType.value === 'model') {
      createVisible.value = false
      await router.push({ path: '/models', query: { create: '1', collectionId: String(collectionId) } })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败')
  } finally {
    createSaving.value = false
  }
}

function onCollectionClick(data: Collection) {
  router.push(`/collections/${data.id}`)
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
          placeholder="搜索问题、仪表盘、模型、集合…"
          @keyup.enter="goSearch"
        >
          <template #append>
            <el-button @click="goSearch">搜索</el-button>
          </template>
        </el-input>
      </div>
      <div class="top-actions">
        <el-dropdown trigger="click" @command="openCreate">
          <el-button type="primary">+ 创建</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="question">问题</el-dropdown-item>
              <el-dropdown-item v-if="userStore.hasPermission('query:raw')" command="sql">SQL 查询</el-dropdown-item>
              <el-dropdown-item command="dashboard">仪表盘</el-dropdown-item>
              <el-dropdown-item command="model">模型</el-dropdown-item>
              <el-dropdown-item command="collection">集合</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <div class="body">
      <aside class="sidebar">
        <nav class="nav-block">
          <router-link class="nav-link" to="/" :class="{ active: route.path === '/' }">首页</router-link>
        </nav>
        <div class="nav-block">
          <div class="nav-group">集合</div>
          <el-tree
            :data="collectionTree"
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="onCollectionClick"
          >
            <template #default="{ data }">
              <span class="tree-node" :class="{ personal: personalIds.has(String(data.id)) }">{{ data.name }}</span>
            </template>
          </el-tree>
        </div>
        <div class="nav-block">
          <div class="nav-group">数据</div>
          <router-link class="nav-link" to="/databases" :class="{ active: route.path.startsWith('/databases') }">数据源</router-link>
          <router-link
            v-if="userStore.hasPermission('query:raw')"
            class="nav-link"
            to="/sql"
            :class="{ active: route.path.startsWith('/sql') }"
          >SQL 查询</router-link>
          <router-link class="nav-link" to="/models" :class="{ active: route.path.startsWith('/models') }">模型</router-link>
          <router-link class="nav-link" to="/metrics" :class="{ active: route.path.startsWith('/metrics') }">指标</router-link>
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
              <button type="button" class="more-btn" title="更多" aria-label="更多菜单">⋯</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="userStore.isAdmin" command="admin">管理端</el-dropdown-item>
                  <el-dropdown-item command="trash">废纸篓</el-dropdown-item>
                  <el-dropdown-item divided command="password">修改密码</el-dropdown-item>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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

    <el-dialog v-model="createVisible" :title="`创建${({ question: '问题', sql: 'SQL 查询', dashboard: '仪表盘', model: '模型', collection: '集合' })[createType]}`" width="460px">
      <el-form label-width="90px">
        <el-form-item v-if="createType !== 'question'" label="名称">
          <el-input v-model="createName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item :label="createType === 'collection' ? '父集合' : '所属集合'">
          <el-select v-model="createCollectionId" class="full-width" :clearable="createType === 'collection'" placeholder="选择集合">
            <el-option v-for="item in flatCollections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <p v-if="createType === 'question'" class="hint">将进入查询工作台保存为问题。</p>
        <p v-if="createType === 'sql'" class="hint">将进入 SQL 查询页；编写完成后可保存为问题并放入所选集合。</p>
        <p v-if="createType === 'model'" class="hint">将进入模型页完成配置。</p>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">继续</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="changePassword">确认修改</el-button>
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
.tree-node { font-size: 13px; }
.tree-node.personal { font-weight: 600; color: var(--omni-accent-strong); }
.content { flex: 1; min-width: 0; background: var(--omni-bg); }
.hint { margin: 0; color: var(--omni-muted); font-size: 13px; }
:deep(.el-tree) { background: transparent; }
:deep(.el-tree-node__content) { border-radius: var(--omni-radius-sm); height: 32px; }
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--omni-accent-soft);
  color: var(--omni-accent-strong);
}
</style>
