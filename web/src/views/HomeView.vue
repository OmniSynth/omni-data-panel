<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { recentApi } from '@/api'
import { resourcePath, resourceTypeLabel } from '@/nav'
import { useUserStore } from '@/stores/user'
import type { RecentItem } from '@/types'

const { t } = useI18n()
const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const recents = ref<RecentItem[]>([])

interface FeatureItem {
  key: string
  titleKey: string
  descKey: string
  to: string
  visible?: boolean
}

const features = computed<FeatureItem[]>(() => [
  {
    key: 'dashboards',
    titleKey: 'home.featureDashboards',
    descKey: 'home.featureDashboardsDesc',
    to: '/dashboards',
  },
  {
    key: 'charts',
    titleKey: 'home.featureCharts',
    descKey: 'home.featureChartsDesc',
    to: '/questions',
  },
  {
    key: 'models',
    titleKey: 'home.featureModels',
    descKey: 'home.featureModelsDesc',
    to: '/models',
  },
  {
    key: 'metrics',
    titleKey: 'home.featureMetrics',
    descKey: 'home.featureMetricsDesc',
    to: '/metrics',
  },
  {
    key: 'databases',
    titleKey: 'home.featureDatabases',
    descKey: 'home.featureDatabasesDesc',
    to: '/databases',
  },
  {
    key: 'sql',
    titleKey: 'home.featureSql',
    descKey: 'home.featureSqlDesc',
    to: '/sql',
    visible: userStore.hasPermission('query:raw'),
  },
].filter((item) => item.visible !== false))

/** 首页常用工具外链，新窗口打开。 */
const toolLinks = [
  { category: 'JSON', name: 'JSON.cn', descKey: 'home.toolJsonFormat', url: 'https://www.json.cn/' },
  { category: 'JSON', name: 'BeJSON', descKey: 'home.toolJsonParse', url: 'https://www.bejson.com/' },
  { category: 'SQL', name: 'SQL Format', descKey: 'home.toolSqlFormat', url: 'https://sqlformat.org/' },
  { category: 'SQL', name: 'DB Diagram', descKey: 'home.toolEr', url: 'https://dbdiagram.io/' },
  { category: 'SQL', name: 'db<>fiddle', descKey: 'home.toolDbFiddle', url: 'https://dbfiddle.uk/' },
  { categoryKey: 'home.catDatabase', nameKey: 'home.toolMysqlName', name: 'MySQL Docs', descKey: 'home.toolMysqlDoc', url: 'https://dev.mysql.com/doc/' },
  { categoryKey: 'home.catDatabase', name: 'ReliaDB Explain', descKey: 'home.toolExplain', url: 'https://reliadb.com/tools/explain/' },
  { categoryKey: 'home.catDatabase', name: 'Dalibo Explain', descKey: 'home.toolPgExplain', url: 'https://explain.dalibo.com/' },
  { categoryKey: 'home.catEncoding', name: 'Base64', descKey: 'home.toolBase64', url: 'https://base64.us/' },
  { categoryKey: 'home.catEncoding', name: 'Regex101', descKey: 'home.toolRegex', url: 'https://regex101.com/' },
  { categoryKey: 'home.catTime', name: 'Tool.lu', descKey: 'home.toolTimestamp', url: 'https://tool.lu/timestamp/' },
  { categoryKey: 'home.catTime', name: 'Cron', descKey: 'home.toolCron', url: 'https://cron.qqe2.com/' },
  { categoryKey: 'home.catGeneral', name: 'Tool.lu', descKey: 'home.toolDevbox', url: 'https://tool.lu/' },
  { categoryKey: 'home.catGeneral', name: 'JWT.io', descKey: 'home.toolJwt', url: 'https://jwt.io/' },
] as const

function toolCategory(tool: (typeof toolLinks)[number]) {
  return 'categoryKey' in tool && tool.categoryKey ? t(tool.categoryKey) : tool.category
}

function toolName(tool: (typeof toolLinks)[number]) {
  return 'nameKey' in tool && tool.nameKey ? t(tool.nameKey) : tool.name
}

async function load() {
  loading.value = true
  try {
    recents.value = await recentApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('home.loadFailed'))
  } finally {
    loading.value = false
  }
}

function open(item: RecentItem) {
  router.push(resourcePath(item.resourceType, item.resourceId))
}

onMounted(load)
</script>

<template>
  <div class="page home">
    <section class="section">
      <h2 class="section-title">{{ t('home.featuresTitle') }}</h2>
      <div class="feature-grid">
        <router-link
          v-for="feature in features"
          :key="feature.key"
          class="feature-card"
          :to="feature.to"
        >
          <strong class="name">{{ t(feature.titleKey) }}</strong>
          <span class="desc">{{ t(feature.descKey) }}</span>
        </router-link>
      </div>
    </section>

    <section class="section">
      <h2 class="section-title">{{ t('home.continueTitle') }}</h2>
      <div v-loading="loading" class="recent-row" :class="{ empty: !loading && !recents.length }">
        <div v-if="!loading && !recents.length" class="recent-empty">
          <span class="recent-empty-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3 2" />
            </svg>
          </span>
          <div class="recent-empty-copy">
            <strong>{{ t('home.emptyRecent') }}</strong>
            <span>{{ t('home.emptyRecentHint') }}</span>
          </div>
          <router-link
            class="recent-empty-action"
            :to="userStore.hasPermission('query:raw') ? '/sql' : '/models'"
          >{{ userStore.hasPermission('query:raw') ? t('home.emptyRecentAction') : t('shell.models') }}</router-link>
        </div>
        <button
          v-for="item in recents"
          :key="`${item.resourceType}-${item.resourceId}`"
          type="button"
          class="recent-card"
          @click="open(item)"
        >
          <span class="type">{{ resourceTypeLabel(item.resourceType) }}</span>
          <strong class="name">{{ item.name }}</strong>
          <span class="desc">{{ item.description || t('common.noDescription') }}</span>
        </button>
      </div>
    </section>

    <section class="section tools-section">
      <h2 class="section-title">{{ t('home.toolsTitle') }}</h2>
      <p class="section-desc">{{ t('home.toolsHint') }}</p>
      <div class="tool-grid">
        <a
          v-for="tool in toolLinks"
          :key="tool.url"
          class="tool-card"
          :href="tool.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="type">{{ toolCategory(tool) }}</span>
          <strong class="name">{{ toolName(tool) }}</strong>
          <span class="desc">{{ t(tool.descKey) }}</span>
        </a>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home { padding: 28px 32px; }
.section + .section { margin-top: 36px; }
.section-title {
  margin: 0 0 14px;
  font-size: 16px;
  color: var(--omni-muted);
  font-weight: 600;
}
.section-desc {
  margin: -6px 0 14px;
  font-size: 13px;
  color: #9ca3af;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.feature-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 96px;
  padding: 18px 16px;
  border: 1px solid var(--omni-border);
  border-radius: 12px;
  background: var(--omni-card);
  text-decoration: none;
  color: inherit;
}
.feature-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--omni-shadow);
}
.feature-card .name {
  font-size: 16px;
  font-weight: 650;
  color: var(--omni-text);
}
.feature-card .desc {
  font-size: 13px;
  color: var(--omni-muted);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.recent-row {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 8px;
  min-height: 140px;
}
.recent-row.empty {
  min-height: 0;
  padding-bottom: 0;
  overflow: visible;
}
.recent-empty {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 88px;
  padding: 16px 20px;
  border: 1px dashed var(--omni-border);
  border-radius: 12px;
  background: var(--omni-surface);
}
.recent-empty-icon {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--omni-card);
  border: 1px solid var(--omni-border);
  color: var(--omni-muted);
}
.recent-empty-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.recent-empty-copy strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--omni-text);
}
.recent-empty-copy span {
  font-size: 13px;
  color: var(--omni-muted);
  line-height: 1.4;
}
.recent-empty-action {
  flex: 0 0 auto;
  padding: 7px 14px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}
.recent-empty-action:hover {
  filter: brightness(1.05);
}
.recent-card {
  flex: 0 0 220px;
  text-align: left;
  border: 1px solid var(--omni-border);
  background: var(--omni-card);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 128px;
  color: inherit;
}
.recent-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--omni-shadow);
}
@media (max-width: 640px) {
  .home { padding: 20px 16px; }
  .recent-empty {
    flex-wrap: wrap;
  }
  .recent-empty-action {
    width: 100%;
    text-align: center;
  }
}
.tools-section .section-title { font-size: 14px; }
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.tool-card {
  text-align: left;
  border: 1px solid transparent;
  background: var(--omni-surface);
  border-radius: var(--omni-radius-sm);
  padding: 12px 14px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  text-decoration: none;
  color: inherit;
}
.tool-card:hover {
  border-color: var(--omni-border);
  background: var(--omni-card);
}
.recent-card .type {
  font-size: 12px;
  color: var(--omni-accent);
  font-weight: 600;
}
.tool-card .type {
  font-size: 11px;
  color: var(--omni-muted);
  font-weight: 600;
}
.recent-card .name {
  font-size: 15px;
  color: var(--omni-text);
}
.tool-card .name {
  font-size: 13px;
  font-weight: 600;
  color: var(--omni-text);
}
.recent-card .desc,
.tool-card .desc {
  font-size: 12px;
  color: #9ca3af;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tool-card .desc {
  font-size: 11px;
  -webkit-line-clamp: 1;
}
</style>
