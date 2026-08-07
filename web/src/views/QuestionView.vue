<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, publicLinkApi, queryApi } from '@/api'
import { displayLabel, formatDateTime } from '@/display'
import type { Chart, PublicLink, QueryResult, QuerySubmission } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import {
  alignNamedParameters,
  alignSqlParameters,
  countSqlPlaceholders,
  extractNamedPlaceholders,
} from '@/sql/parameters'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const chart = ref<Chart>()
const result = ref<QueryResult>()
const error = ref('')
const links = ref<PublicLink[]>([])
const sharing = ref(false)
const expiresInDays = ref<0 | 1 | 7 | 30 | 90>(0)
const expireOptions: { value: 0 | 1 | 7 | 30 | 90; labelKey: string }[] = [
  { value: 0, labelKey: 'dashboard.expireNever' },
  { value: 1, labelKey: 'dashboard.expireDays' },
  { value: 7, labelKey: 'dashboard.expireDays' },
  { value: 30, labelKey: 'dashboard.expireDays' },
  { value: 90, labelKey: 'dashboard.expireDays' },
]
const sqlParameters = ref<string[]>([])
const namedSqlParameters = reactive<Record<string, string>>({})
const namedParamNames = computed(() => extractNamedPlaceholders(submission.value?.sql || ''))
const submission = ref<QuerySubmission>()

const chartId = computed(() => String(route.params.id))
const hasSqlParams = computed(() =>
  namedParamNames.value.length > 0 || countSqlPlaceholders(submission.value?.sql || '') > 0)
const activeLinks = computed(() => links.value.filter(isActiveQuestionLink))

async function load() {
  loading.value = true
  error.value = ''
  result.value = undefined
  links.value = []
  try {
    chart.value = await chartApi.get(chartId.value)
    submission.value = JSON.parse(chart.value.queryJson) as QuerySubmission
    if (submission.value.sql) {
      const named = alignNamedParameters(submission.value.sql, submission.value.namedParameters)
      for (const key of Object.keys(namedSqlParameters)) delete namedSqlParameters[key]
      for (const [key, value] of Object.entries(named)) {
        namedSqlParameters[key] = String(value ?? '')
      }
      sqlParameters.value = alignSqlParameters(
        submission.value.sql,
        (submission.value.parameters || []).map(String),
      ).map((item) => String(item ?? ''))
    } else {
      for (const key of Object.keys(namedSqlParameters)) delete namedSqlParameters[key]
      sqlParameters.value = []
    }
    await Promise.all([runPreview(), loadLinks()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('chart.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function runPreview() {
  if (!chart.value || !submission.value) return
  try {
    const payload: QuerySubmission = submission.value.sql
      ? {
        sourceId: submission.value.sourceId,
        sql: submission.value.sql,
        parameters: alignSqlParameters(submission.value.sql, sqlParameters.value),
        namedParameters: namedParamNames.value.length
          ? Object.fromEntries(namedParamNames.value.map((name) => [name, namedSqlParameters[name] ?? '']))
          : undefined,
      }
      : submission.value
    const { queryId } = await queryApi.submit(payload)
    while (true) {
      const snapshot = await queryApi.status(queryId)
      if (snapshot.status === 'SUCCEEDED') {
        result.value = snapshot.result
        return
      }
      if (snapshot.status === 'FAILED' || snapshot.status === 'CANCELLED') {
        throw new Error(snapshot.error || t('chart.queryFailed'))
      }
      await new Promise((resolve) => window.setTimeout(resolve, 500))
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('question.previewFailed')
  }
}

function isActiveQuestionLink(link: PublicLink) {
  return link.enabled !== false
    && link.resourceType === 'QUESTION'
    && String(link.resourceId) === chartId.value
}

async function loadLinks() {
  if (!chart.value) return
  try {
    const all = await publicLinkApi.list({ resourceType: 'QUESTION', resourceId: chartId.value })
    links.value = all.filter(isActiveQuestionLink)
  } catch {
    links.value = []
  }
}

async function createLink() {
  sharing.value = true
  try {
    const link = await publicLinkApi.create({
      resourceType: 'QUESTION',
      resourceId: chartId.value,
      expiresInDays: expiresInDays.value === 0 ? null : expiresInDays.value,
    })
    links.value = [link, ...links.value.filter((item) => item.token !== link.token)]
    ElMessage.success(t('chart.linkCreated'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('chart.linkCreateFailed'))
  } finally {
    sharing.value = false
  }
}

function isExpired(link: PublicLink) {
  if (!link.expiresAt) return false
  const ts = Date.parse(link.expiresAt)
  return Number.isFinite(ts) && ts <= Date.now()
}

function expireLabel(link: PublicLink) {
  if (!link.expiresAt) return t('dashboard.expireNever')
  if (isExpired(link)) return t('dashboard.expired')
  return t('dashboard.expiresAt', { time: formatDateTime(link.expiresAt) })
}

async function revokeLink(link: PublicLink) {
  const previous = links.value
  links.value = links.value.filter((item) => item.token !== link.token)
  try {
    await publicLinkApi.revoke(link.id)
    ElMessage.success(t('chart.revoked'))
  } catch (err) {
    links.value = previous
    ElMessage.error(err instanceof Error ? err.message : t('chart.revokeFailed'))
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
        <h1 class="page-title">{{ chart?.name || t('chart.title') }}</h1>
        <p class="muted">{{ chart?.description || displayLabel(chart?.chartType) }}</p>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button type="primary" @click="router.push({ path: '/query', query: { questionId: chartId } })">{{ t('chart.edit') }}</el-button>
        <el-select v-model="expiresInDays" style="width: 140px">
          <el-option
            v-for="opt in expireOptions"
            :key="opt.value"
            :label="opt.value === 0 ? t(opt.labelKey) : t(opt.labelKey, { n: opt.value })"
            :value="opt.value"
          />
        </el-select>
        <el-button :loading="sharing" plain @click="createLink">{{ t('chart.publicShare') }}</el-button>
      </div>
    </div>
    <el-card v-if="hasSqlParams" class="mb">
      <div class="param-title">{{ t('chart.queryParams') }}</div>
      <div v-for="name in namedParamNames" :key="name" class="param-row">
        <el-input v-model="namedSqlParameters[name]" :placeholder="`:${name}`" style="max-width:320px">
          <template #prepend>{{ name }}</template>
        </el-input>
      </div>
      <div v-for="(_, index) in sqlParameters" :key="`pos-${index}`" class="param-row">
        <el-input v-model="sqlParameters[index]" :placeholder="t('chart.paramN', { n: index + 1 })" style="max-width:320px" />
      </div>
      <el-button type="primary" @click="runPreview">{{ t('chart.applyRerun') }}</el-button>
    </el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb" />
    <el-card>
      <ChartPreview
        v-if="chart"
        :type="chart.chartType"
        :result="result"
        :option="JSON.parse(chart.configJson || '{}')"
        :fill="false"
      />
    </el-card>
    <el-card v-if="activeLinks.length" class="mt">
      <div class="card-title">{{ t('chart.publicLinks') }}</div>
      <div v-for="link in activeLinks" :key="link.token" class="link-row">
        <div class="link-main">
          <a :href="publicUrl(link.token)" target="_blank" rel="noreferrer">{{ publicUrl(link.token) }}</a>
          <span class="expire-meta" :class="{ expired: isExpired(link) }">{{ expireLabel(link) }}</span>
        </div>
        <el-button link type="danger" @click="revokeLink(link)">{{ t('chart.revoke') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.param-title { font-weight: 600; margin-bottom: 8px; }
.param-row { margin-bottom: 8px; }
.link-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 8px; }
.link-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.expire-meta { font-size: 12px; color: var(--el-text-color-secondary); }
.expire-meta.expired { color: var(--el-color-danger); }
.card-title { font-weight: 600; margin-bottom: 8px; }
.muted { color: var(--el-text-color-secondary); margin: 4px 0 0; }
</style>
