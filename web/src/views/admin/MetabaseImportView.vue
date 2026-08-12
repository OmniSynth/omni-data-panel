<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { collectionApi, dataSourceApi, metabaseImportApi } from '@/api'
import type {
  Collection,
  DataSource,
  MetabaseDashboardSummary,
  MetabaseImportResult,
  MetabasePreviewResult,
} from '@/types'

const { t } = useI18n()
const router = useRouter()

const step = ref(0)
const connecting = ref(false)
const previewing = ref(false)
const importing = ref(false)

const baseUrl = ref('')
const apiKey = ref('')
const keyword = ref('')
const dashboards = ref<MetabaseDashboardSummary[]>([])
const selectedId = ref<number>()
const preview = ref<MetabasePreviewResult>()
const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const collectionId = ref<string>()
/** Metabase databaseId -> Omni dataSourceId */
const databaseMap = ref<Record<string, string>>({})
const result = ref<MetabaseImportResult>()

function flattenCollections(nodes: Collection[], out: Collection[] = []): Collection[] {
  for (const node of nodes) {
    out.push(node)
    if (Array.isArray((node as Collection & { children?: Collection[] }).children)) {
      flattenCollections((node as Collection & { children?: Collection[] }).children!, out)
    }
  }
  return out
}

const filteredDashboards = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return dashboards.value
  return dashboards.value.filter((item) => item.name.toLowerCase().includes(q))
})

async function loadLocalOptions() {
  try {
    const [sourceList, collectionList] = await Promise.all([
      dataSourceApi.list(),
      collectionApi.tree(),
    ])
    sources.value = sourceList
    collections.value = flattenCollections(collectionList)
  } catch {
    sources.value = []
    collections.value = []
  }
}

async function connect() {
  if (!baseUrl.value.trim() || !apiKey.value.trim()) {
    ElMessage.warning(t('metabaseImport.needCredential'))
    return
  }
  connecting.value = true
  try {
    dashboards.value = await metabaseImportApi.listDashboards({
      baseUrl: baseUrl.value.trim(),
      apiKey: apiKey.value.trim(),
    })
    selectedId.value = undefined
    preview.value = undefined
    step.value = 1
    ElMessage.success(t('metabaseImport.connected', { n: dashboards.value.length }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metabaseImport.connectFailed'))
  } finally {
    connecting.value = false
  }
}

async function runPreview() {
  if (!selectedId.value) {
    ElMessage.warning(t('metabaseImport.needDashboard'))
    return
  }
  previewing.value = true
  try {
    preview.value = await metabaseImportApi.preview({
      baseUrl: baseUrl.value.trim(),
      apiKey: apiKey.value.trim(),
      metabaseDashboardId: selectedId.value,
    })
    const next: Record<string, string> = {}
    for (const id of preview.value.databaseIds) {
      next[String(id)] = databaseMap.value[String(id)] || ''
    }
    databaseMap.value = next
    step.value = 2
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metabaseImport.previewFailed'))
  } finally {
    previewing.value = false
  }
}

async function runImport() {
  if (!preview.value || !selectedId.value) return
  for (const id of preview.value.databaseIds) {
    if (!databaseMap.value[String(id)]) {
      ElMessage.warning(t('metabaseImport.needMapping'))
      return
    }
  }
  if (!preview.value.importableCards.length) {
    ElMessage.warning(t('metabaseImport.noImportable'))
    return
  }
  importing.value = true
  try {
    const map: Record<string, string> = {}
    for (const [k, v] of Object.entries(databaseMap.value)) {
      map[k] = v
    }
    result.value = await metabaseImportApi.importDashboard({
      baseUrl: baseUrl.value.trim(),
      apiKey: apiKey.value.trim(),
      metabaseDashboardId: selectedId.value,
      databaseMap: map,
      collectionId: collectionId.value || undefined,
    })
    step.value = 3
    ElMessage.success(t('metabaseImport.importDone'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metabaseImport.importFailed'))
  } finally {
    importing.value = false
  }
}

function openDashboard() {
  if (!result.value) return
  router.push(`/dashboards/${result.value.dashboardId}/view`)
}

onMounted(() => {
  void loadLocalOptions()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('metabaseImport.title') }}</h1>
    </div>
    <p class="hint">{{ t('metabaseImport.hint') }}</p>

    <el-steps :active="step" finish-status="success" align-center class="steps">
      <el-step :title="t('metabaseImport.stepConnect')" />
      <el-step :title="t('metabaseImport.stepPick')" />
      <el-step :title="t('metabaseImport.stepMap')" />
      <el-step :title="t('metabaseImport.stepDone')" />
    </el-steps>

    <el-card v-if="step === 0" class="panel">
      <el-form label-width="120px">
        <el-form-item :label="t('metabaseImport.baseUrl')">
          <el-input v-model="baseUrl" placeholder="https://metabase.example.com" />
        </el-form-item>
        <el-form-item :label="t('metabaseImport.apiKey')">
          <el-input v-model="apiKey" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="connecting" @click="connect">
            {{ t('metabaseImport.connect') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-else-if="step === 1" class="panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          :placeholder="t('metabaseImport.searchDashboard')"
          style="max-width: 280px"
        />
        <el-button @click="step = 0">{{ t('common.back') }}</el-button>
        <el-button type="primary" :loading="previewing" @click="runPreview">
          {{ t('metabaseImport.preview') }}
        </el-button>
      </div>
      <el-table
        :data="filteredDashboards"
        highlight-current-row
        max-height="420"
        @current-change="(row: MetabaseDashboardSummary | undefined) => { selectedId = row?.id }"
      >
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="name" :label="t('metabaseImport.dashboardName')" min-width="220" />
      </el-table>
    </el-card>

    <el-card v-else-if="step === 2 && preview" class="panel">
      <h3 class="sub">{{ preview.name }}</h3>
      <p class="muted">
        {{ t('metabaseImport.previewSummary', {
          ok: preview.importableCards.length,
          skip: preview.skippedCards.length,
          params: preview.parameters.length,
        }) }}
      </p>

      <h4 class="sub">{{ t('metabaseImport.databaseMap') }}</h4>
      <div v-for="dbId in preview.databaseIds" :key="dbId" class="map-row">
        <span>Metabase DB #{{ dbId }}</span>
        <el-select
          v-model="databaseMap[String(dbId)]"
          clearable
          filterable
          :placeholder="t('metabaseImport.pickDataSource')"
          style="min-width: 260px"
        >
          <el-option
            v-for="source in sources"
            :key="source.id"
            :label="source.name"
            :value="String(source.id)"
          />
        </el-select>
      </div>

      <h4 class="sub">{{ t('metabaseImport.collection') }}</h4>
      <el-select
        v-model="collectionId"
        clearable
        filterable
        :placeholder="t('metabaseImport.collectionOptional')"
        style="min-width: 260px"
      >
        <el-option
          v-for="item in collections"
          :key="item.id"
          :label="item.name"
          :value="String(item.id)"
        />
      </el-select>

      <h4 class="sub">{{ t('metabaseImport.parameters') }}</h4>
      <el-table :data="preview.parameters" size="small" empty-text="-">
        <el-table-column prop="label" :label="t('metabaseImport.paramLabel')" />
        <el-table-column prop="type" :label="t('metabaseImport.paramType')" width="120" />
        <el-table-column prop="bindName" :label="t('metabaseImport.paramBind')" width="140" />
      </el-table>

      <h4 class="sub">{{ t('metabaseImport.importable') }}</h4>
      <el-table :data="preview.importableCards" size="small" max-height="220">
        <el-table-column prop="name" :label="t('metabaseImport.cardName')" />
        <el-table-column prop="chartType" :label="t('common.type')" width="100" />
        <el-table-column prop="databaseId" label="DB" width="80" />
      </el-table>

      <h4 class="sub">{{ t('metabaseImport.skipped') }}</h4>
      <el-table :data="preview.skippedCards" size="small" max-height="180" empty-text="-">
        <el-table-column prop="name" :label="t('metabaseImport.cardName')" />
        <el-table-column prop="reason" :label="t('metabaseImport.skipReason')" />
      </el-table>

      <div class="actions">
        <el-button @click="step = 1">{{ t('common.back') }}</el-button>
        <el-button type="primary" :loading="importing" @click="runImport">
          {{ t('metabaseImport.import') }}
        </el-button>
      </div>
    </el-card>

    <el-card v-else-if="step === 3 && result" class="panel">
      <el-result icon="success" :title="t('metabaseImport.importDone')">
        <template #sub-title>
          {{ result.dashboardName }}（ID {{ result.dashboardId }}）·
          {{ t('metabaseImport.chartsCreated', { n: result.chartIds.length }) }}
        </template>
        <template #extra>
          <el-button type="primary" @click="openDashboard">{{ t('metabaseImport.openDashboard') }}</el-button>
          <el-button @click="step = 0; result = undefined">{{ t('metabaseImport.importAnother') }}</el-button>
        </template>
      </el-result>
      <el-alert
        v-if="result.warnings?.length"
        type="warning"
        :closable="false"
        class="mt"
        :title="t('metabaseImport.warnings')"
      >
        <ul>
          <li v-for="(w, i) in result.warnings" :key="i">{{ w }}</li>
        </ul>
      </el-alert>
      <el-alert
        v-if="result.skippedCards?.length"
        type="info"
        :closable="false"
        class="mt"
        :title="t('metabaseImport.skipped')"
      >
        <ul>
          <li v-for="(c, i) in result.skippedCards" :key="i">{{ c.name }}：{{ c.reason }}</li>
        </ul>
      </el-alert>
    </el-card>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 16px; }
.steps { margin-bottom: 20px; }
.panel { max-width: 960px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.sub { margin: 16px 0 8px; }
.muted { color: var(--el-text-color-secondary); margin: 0 0 8px; }
.map-row { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
.mt { margin-top: 12px; }
</style>
