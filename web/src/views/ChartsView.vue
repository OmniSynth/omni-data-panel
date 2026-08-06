<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { chartApi, collectionApi, publicLinkApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { requiredRule, validateForm } from '@/form/rules'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, Id, QueryResult, QuerySubmission } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import ChartEncodingForm from '@/components/ChartEncodingForm.vue'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'
import { chartTypeOptions, mergeChartConfig, parseChartConfig, type ChartEncoding } from '@/dashboard/config'
import { copyText } from '@/utils/clipboard'

const { t } = useI18n()
const userStore = useUserStore()
const router = useRouter()
const rows = ref<Chart[]>([])
const collections = ref<Collection[]>([])
const visible = ref(false)
const formRef = ref<FormInstance>()
const formRules = computed<FormRules>(() => ({
  name: [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))],
  chartType: [requiredRule(t('common.pleaseSelect', { field: t('common.type') }), 'change')],
}))
const preview = ref<Chart>()
const previewResult = ref<QueryResult>()
const previewEncoding = ref<ChartEncoding>({})
const previewDrillPath = ref<string[]>([])
const previewSaving = ref(false)
const permissionVisible = ref(false)
const permissionChart = ref<Chart>()
const chartTypeOptionList = computed(() => chartTypeOptions())
const form = reactive<Chart>({
  id: '', name: '', datasetId: '', queryJson: '{}', chartType: 'bar', configJson: '{}',
  description: '', collectionId: '',
})

const previewColumns = computed(() => previewResult.value?.columns || [])

function flatten(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flatten(node.children, acc)
  }
  return acc
}

async function load() {
  try {
    const [chartList, tree] = await Promise.all([chartApi.list(), collectionApi.tree()])
    rows.value = chartList
    collections.value = flatten(tree)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('chart.loadFailed'))
  }
}

function edit(row: Chart) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  visible.value = true
}

async function save() {
  if (!(await validateForm(formRef.value))) return
  try {
    await chartApi.update(form.id, {
      name: form.name,
      datasetId: form.datasetId,
      dataSourceId: form.dataSourceId,
      queryJson: form.queryJson,
      chartType: form.chartType,
      configJson: form.configJson,
      description: form.description,
      collectionId: form.collectionId,
    })
    visible.value = false
    ElMessage.success(t('chart.saved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('chart.moveToTrash'), t('common.deleteConfirmTitle'))
    await chartApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

async function showPreview(chart: Chart) {
  preview.value = chart
  previewResult.value = undefined
  const parsed = parseChartConfig(chart.configJson)
  previewEncoding.value = parsed.encoding || {}
  previewDrillPath.value = parsed.drillPath || []
  try {
    const { queryId } = await queryApi.submit(JSON.parse(chart.queryJson) as QuerySubmission)
    while (true) {
      const snapshot = await queryApi.status(queryId)
      if (snapshot.status === 'SUCCEEDED') {
        previewResult.value = snapshot.result
        return
      }
      if (snapshot.status === 'FAILED' || snapshot.status === 'CANCELLED') {
        throw new Error(snapshot.error || t('chart.queryFailed'))
      }
      await new Promise((resolve) => window.setTimeout(resolve, 500))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('chart.previewFailed'))
  }
}

async function savePreviewEncoding() {
  if (!preview.value) return
  previewSaving.value = true
  try {
    const configJson = mergeChartConfig(
      parseChartConfig(preview.value.configJson),
      previewEncoding.value,
      previewDrillPath.value,
    )
    const updated = await chartApi.update(preview.value.id, {
      name: preview.value.name,
      datasetId: preview.value.datasetId,
      dataSourceId: preview.value.dataSourceId,
      queryJson: preview.value.queryJson,
      chartType: preview.value.chartType,
      configJson,
      description: preview.value.description,
      collectionId: preview.value.collectionId,
    })
    preview.value = updated
    ElMessage.success(t('chart.encodingSaved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    previewSaving.value = false
  }
}

async function share(chart: Chart) {
  try {
    const link = await publicLinkApi.create({ resourceType: 'QUESTION', resourceId: chart.id })
    const url = `${location.origin}/public/question/${link.token}`
    if (await copyText(url)) {
      ElMessage.success(t('chart.linkCopied'))
    } else {
      ElMessage.success(t('chart.linkCreated'))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('chart.linkCreateFailed'))
  }
}

function collectionName(id?: Id) {
  if (id === undefined || id === null || id === '') return t('common.emptyDash')
  return collections.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

function canShare(row: Chart) {
  return userStore.isAdmin || String(row.ownerId) === String(userStore.user?.id)
}

function authorize(row: Chart) {
  permissionChart.value = row
  permissionVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('chart.title') }}</h1>
      <el-button v-if="userStore.hasPermission('chart:create')" type="primary" @click="router.push('/query')">{{ t('chart.create') }}</el-button>
    </div>
    <el-table :data="rows" :empty-text="t('chart.empty')">
      <el-table-column prop="name" :label="t('common.name')" min-width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/questions/${row.id}`)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
      <el-table-column :label="t('common.type')" width="100">
        <template #default="{ row }">{{ displayLabel(row.chartType) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.collection')" width="160">
        <template #default="{ row }">{{ collectionName(row.collectionId) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="380">
        <template #default="{ row }">
          <el-button link @click="showPreview(row)">{{ t('common.preview') }}</el-button>
          <el-button link type="primary" @click="router.push({ path: '/query', query: { questionId: String(row.id) } })">{{ t('common.edit') }}</el-button>
          <el-button link @click="edit(row)">{{ t('chart.properties') }}</el-button>
          <el-button link @click="share(row)">{{ t('common.share') }}</el-button>
          <el-button v-if="canShare(row)" link type="primary" @click="authorize(row)">{{ t('chart.roleShare') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="CHART"
      :resource-id="permissionChart?.id"
      :allowed-permissions="['READ', 'WRITE']"
      :title="`${t('chart.roleShareTitle')}${permissionChart?.name || ''}`"
      :hint="t('roleGrant.resourceHint')"
    />

    <el-dialog v-model="visible" :title="t('chart.properties')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="t('common.type')" prop="chartType">
          <el-select v-model="form.chartType" class="full-width">
            <el-option
              v-for="option in chartTypeOptionList"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.collection')">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog :model-value="!!preview" :title="t('chart.preview')" width="800px" @close="preview=undefined">
      <template v-if="preview">
        <ChartEncodingForm
          v-if="previewColumns.length"
          v-model="previewEncoding"
          v-model:drill-path="previewDrillPath"
          :columns="previewColumns"
          :chart-type="preview.chartType"
        />
        <div v-if="previewColumns.length" class="preview-actions">
          <el-button type="primary" :loading="previewSaving" @click="savePreviewEncoding">{{ t('chart.saveEncoding') }}</el-button>
        </div>
        <ChartPreview
          :type="preview.chartType"
          :result="previewResult"
          :option="JSON.parse(mergeChartConfig(parseChartConfig(preview.configJson), previewEncoding, previewDrillPath))"
        />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.preview-actions { margin: 8px 0 12px; }
.full-width { width: 100%; }
</style>
