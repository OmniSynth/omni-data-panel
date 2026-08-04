<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, publicLinkApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, PublicLink, QueryResult, QuerySubmission } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const chart = ref<Chart>()
const result = ref<QueryResult>()
const error = ref('')
const links = ref<PublicLink[]>([])
const sharing = ref(false)

const chartId = computed(() => String(route.params.id))

async function load() {
  loading.value = true
  error.value = ''
  result.value = undefined
  try {
    chart.value = await chartApi.get(chartId.value)
    await Promise.all([runPreview(), loadLinks()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '问题加载失败')
  } finally {
    loading.value = false
  }
}

async function runPreview() {
  if (!chart.value) return
  try {
    const submission = JSON.parse(chart.value.queryJson) as QuerySubmission
    const { queryId } = await queryApi.submit(submission)
    while (true) {
      const snapshot = await queryApi.status(queryId)
      if (snapshot.status === 'SUCCEEDED') {
        result.value = snapshot.result
        return
      }
      if (snapshot.status === 'FAILED' || snapshot.status === 'CANCELLED') {
        throw new Error(snapshot.error || '问题查询未成功')
      }
      await new Promise((resolve) => window.setTimeout(resolve, 500))
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '预览失败'
  }
}

async function loadLinks() {
  if (!userStore.isAdmin && !chart.value) return
  try {
    links.value = await publicLinkApi.list({ resourceType: 'QUESTION', resourceId: chartId.value })
  } catch {
    links.value = []
  }
}

async function createLink() {
  sharing.value = true
  try {
    const link = await publicLinkApi.create({ resourceType: 'QUESTION', resourceId: chartId.value })
    links.value = [link, ...links.value.filter((item) => String(item.id) !== String(link.id))]
    ElMessage.success('公开链接已创建')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建公开链接失败')
  } finally {
    sharing.value = false
  }
}

async function revokeLink(id: string | number) {
  try {
    await publicLinkApi.revoke(id)
    ElMessage.success('已撤销')
    await loadLinks()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '撤销失败')
  }
}

function publicUrl(token: string) {
  return `${location.origin}/public/question/${token}`
}

watch(chartId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ chart?.name || '问题' }}</h1>
        <p class="muted">{{ chart?.description || displayLabel(chart?.chartType) }}</p>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button @click="router.push({ path: '/query', query: { questionId: chartId } })">在工作台打开</el-button>
        <el-button :loading="sharing" type="primary" plain @click="createLink">公开分享</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb" />
    <el-card>
      <ChartPreview
        v-if="chart"
        :type="chart.chartType"
        :result="result"
        :option="JSON.parse(chart.configJson || '{}')"
      />
    </el-card>
    <el-card v-if="links.length" class="mt">
      <div class="card-title">公开链接</div>
      <div v-for="link in links" :key="link.id" class="link-row">
        <el-input :model-value="publicUrl(link.token)" readonly />
        <el-button link type="danger" @click="revokeLink(link.id)">撤销</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 14px; }
.mt { margin-top: 16px; }
.link-row { display: flex; gap: 10px; margin-bottom: 8px; align-items: center; }
</style>
