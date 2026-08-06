<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { marked } from 'marked'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import userGuideMd from '../../../docs/user-guide.md?raw'
import apiLogGuideMd from '../../../docs/api-log-dashboard-guide.md?raw'
import oidcSsoMd from '../../../docs/oidc-sso.md?raw'
import embedMd from '../../../docs/embed-integration.md?raw'
import productionMd from '../../../docs/production.md?raw'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const TAB_IDS = ['guide', 'api-log', 'oidc', 'embed', 'production'] as const
type TabId = (typeof TAB_IDS)[number]

function resolveTab(value: unknown): TabId {
  return typeof value === 'string' && (TAB_IDS as readonly string[]).includes(value)
    ? (value as TabId)
    : 'guide'
}

const active = ref<TabId>(resolveTab(route.query.tab))

watch(
  () => route.query.tab,
  (tab) => {
    active.value = resolveTab(tab)
  },
)

watch(active, (tab) => {
  if (route.query.tab === tab) return
  router.replace({ query: { ...route.query, tab } })
})

marked.setOptions({ gfm: true, breaks: false })

/** 相对图片与文档内链改写为应用内可访问路径 */
function rewriteMarkdown(source: string): string {
  return source
    .replace(/\]\(api-log-dashboard-guide\.md\)/g, '](#api-log)')
    .replace(/\]\(user-guide\.md\)/g, '](#guide)')
    .replace(/\]\(oidc-sso\.md\)/g, '](#oidc)')
    .replace(/\]\(embed-integration\.md\)/g, '](#embed)')
    .replace(/\]\(production\.md\)/g, '](#production)')
    .replace(/\[([^\]]+)\]\(\.\.\/README\.md\)/g, '$1')
    .replace(/!\[([^\]]*)\]\(assets\//g, '![$1](/docs-assets/')
    .replace(/```mermaid[\s\S]*?```/g, (block) => {
      const body = block.replace(/^```mermaid\s*/, '').replace(/```$/, '').trim()
      return `<pre class="mermaid-fallback"><code>${escapeHtml(body)}</code></pre>`
    })
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

const sources: Record<TabId, string> = {
  guide: userGuideMd,
  'api-log': apiLogGuideMd,
  oidc: oidcSsoMd,
  embed: embedMd,
  production: productionMd,
}

const htmlByTab = computed(() => {
  const result = {} as Record<TabId, string>
  for (const id of TAB_IDS) {
    result[id] = marked.parse(rewriteMarkdown(sources[id]), { async: false }) as string
  }
  return result
})

function onDocClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLElement)) return
  const anchor = target.closest('a')
  if (!anchor) return
  const href = anchor.getAttribute('href')
  if (!href || !href.startsWith('#')) return
  const tab = href.slice(1)
  if ((TAB_IDS as readonly string[]).includes(tab)) {
    event.preventDefault()
    active.value = tab as TabId
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>{{ t('help.title') }}</h2>
        <p class="muted">{{ t('help.subtitle') }}</p>
      </div>
    </div>

    <el-tabs v-model="active" class="guide-tabs">
      <el-tab-pane :label="t('help.tabGuide')" name="guide" />
      <el-tab-pane :label="t('help.tabApiLog')" name="api-log" />
      <el-tab-pane :label="t('help.tabOidc')" name="oidc" />
      <el-tab-pane :label="t('help.tabEmbed')" name="embed" />
      <el-tab-pane :label="t('help.tabProduction')" name="production" />
    </el-tabs>

    <article
      class="guide-body"
      @click="onDocClick"
      v-html="htmlByTab[active]"
    />
  </div>
</template>

<style scoped>
.page { max-width: 920px; }
.muted { margin: 4px 0 0; color: var(--el-text-color-secondary); }
.guide-tabs { margin-bottom: 8px; }
.guide-body {
  padding: 8px 4px 48px;
  line-height: 1.65;
  color: var(--el-text-color-primary);
  font-size: 14px;
}
.guide-body :deep(h1) {
  margin: 0 0 16px;
  font-size: 26px;
  line-height: 1.3;
}
.guide-body :deep(h2) {
  margin: 28px 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 20px;
}
.guide-body :deep(h3) {
  margin: 20px 0 8px;
  font-size: 16px;
}
.guide-body :deep(p),
.guide-body :deep(ul),
.guide-body :deep(ol) {
  margin: 0 0 12px;
}
.guide-body :deep(li) { margin: 4px 0; }
.guide-body :deep(blockquote) {
  margin: 0 0 12px;
  padding: 8px 14px;
  border-left: 3px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.guide-body :deep(table) {
  width: 100%;
  margin: 0 0 16px;
  border-collapse: collapse;
  font-size: 13px;
}
.guide-body :deep(th),
.guide-body :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color);
  text-align: left;
  vertical-align: top;
}
.guide-body :deep(th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}
.guide-body :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12.5px;
}
.guide-body :deep(pre) {
  margin: 0 0 16px;
  padding: 12px 14px;
  overflow: auto;
  border-radius: 8px;
  background: var(--el-fill-color-dark);
  color: var(--el-color-white);
}
.guide-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}
.guide-body :deep(img) {
  display: block;
  max-width: 100%;
  margin: 8px 0 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.guide-body :deep(a) {
  color: var(--el-color-primary);
  text-decoration: none;
}
.guide-body :deep(a:hover) { text-decoration: underline; }
.guide-body :deep(hr) {
  margin: 24px 0;
  border: none;
  border-top: 1px solid var(--el-border-color-lighter);
}
.guide-body :deep(.mermaid-fallback) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
</style>
