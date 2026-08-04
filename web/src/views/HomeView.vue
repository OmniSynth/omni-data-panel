<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { recentApi } from '@/api'
import { resourcePath, resourceTypeLabel } from '@/nav'
import { useUserStore } from '@/stores/user'
import type { RecentItem } from '@/types'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const recents = ref<RecentItem[]>([])

/** 首页常用工具外链，新窗口打开。 */
const toolLinks = [
  { category: 'JSON', name: 'JSON.cn', desc: 'JSON 格式化、校验与压缩', url: 'https://www.json.cn/' },
  { category: 'JSON', name: 'BeJSON', desc: 'JSON 解析、对比与转义', url: 'https://www.bejson.com/' },
  { category: 'SQL', name: 'SQL Format', desc: 'SQL 美化与格式化', url: 'https://sqlformat.org/' },
  { category: 'SQL', name: 'DB Diagram', desc: '在线绘制数据库 ER 图', url: 'https://dbdiagram.io/' },
  { category: '数据库', name: 'MySQL 文档', desc: '官方手册与函数参考', url: 'https://dev.mysql.com/doc/' },
  { category: '数据库', name: 'Explain.dev', desc: '执行计划可视化分析', url: 'https://explain.dev/' },
  { category: '编码', name: 'Base64', desc: 'Base64 编解码', url: 'https://base64.us/' },
  { category: '编码', name: '正则测试', desc: '正则表达式在线调试', url: 'https://regex101.com/' },
  { category: '时间', name: '时间戳转换', desc: 'Unix 时间戳与日期互转', url: 'https://tool.lu/timestamp/' },
  { category: '时间', name: 'Cron 表达式', desc: 'Cron 生成与下次运行预览', url: 'https://cron.qqe2.com/' },
  { category: '综合', name: 'Tool.lu', desc: '程序员在线工具箱', url: 'https://tool.lu/' },
  { category: '综合', name: 'JWT.io', desc: 'JWT 解码与校验', url: 'https://jwt.io/' },
] as const

const greeting = computed(() => {
  const name = userStore.user?.displayName || userStore.user?.username || '朋友'
  return `近况如何，${name}?`
})

async function load() {
  loading.value = true
  try {
    recents.value = await recentApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '最近项加载失败')
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
    <h1 class="greeting">{{ greeting }}</h1>
    <section class="section">
      <h2 class="section-title">从你离开的地方继续</h2>
      <div v-loading="loading" class="recent-row">
        <el-empty v-if="!loading && !recents.length" description="暂无最近访问，去集合里创建内容吧" />
        <button
          v-for="item in recents"
          :key="`${item.resourceType}-${item.resourceId}`"
          type="button"
          class="recent-card"
          @click="open(item)"
        >
          <span class="type">{{ resourceTypeLabel(item.resourceType) }}</span>
          <strong class="name">{{ item.name }}</strong>
          <span class="desc">{{ item.description || '无描述' }}</span>
        </button>
      </div>
    </section>

    <section class="section tools-section">
      <h2 class="section-title">常用工具</h2>
      <p class="section-desc">JSON、SQL、数据库与编码等外部站点，点击在新标签页打开。</p>
      <div class="tool-grid">
        <a
          v-for="tool in toolLinks"
          :key="tool.url"
          class="tool-card"
          :href="tool.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="type">{{ tool.category }}</span>
          <strong class="name">{{ tool.name }}</strong>
          <span class="desc">{{ tool.desc }}</span>
        </a>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home { padding: 28px 32px; }
.greeting {
  margin: 0 0 28px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--omni-text);
}
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
.recent-row {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 8px;
  min-height: 140px;
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
.desc {
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
