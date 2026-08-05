<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi, datasetApi, metricApi } from '@/api'
import { displayLabel } from '@/display'
import { requiredRule, validateForm } from '@/form/rules'
import { useUserStore } from '@/stores/user'
import type { Collection, Dataset, DatasetField, Id, Metric } from '@/types'

const { t } = useI18n()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const rows = ref<Metric[]>([])
const models = ref<Dataset[]>([])
const collections = ref<Collection[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const formRef = ref<FormInstance>()
const form = reactive<Partial<Metric> & { field?: string }>({
  name: '', description: '', modelId: '', expressionJson: '{"field":""}', aggregation: 'SUM', collectionId: '', field: '',
})

const aggregationOptions = ['SUM', 'AVG', 'COUNT', 'MAX', 'MIN'] as const

const formRules = computed<FormRules>(() => ({
  name: [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))],
  modelId: [requiredRule(t('common.pleaseSelect', { field: t('metric.model') }), 'change')],
  field: [requiredRule(t('common.pleaseSelect', { field: t('metric.refField') }), 'change')],
  aggregation: [requiredRule(t('common.pleaseSelect', { field: t('metric.aggregation') }), 'change')],
}))

const modelFields = computed(() => {
  const model = models.value.find((item) => String(item.id) === String(form.modelId))
  return model?.fields || []
})

const expressionField = computed({
  get: () => form.field || '',
  set: (field: string) => {
    form.field = field || ''
    form.expressionJson = JSON.stringify({ field: field || '' })
  },
})

function flatten(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flatten(node.children, acc)
  }
  return acc
}

async function load() {
  try {
    const [metricList, modelList, tree] = await Promise.all([
      metricApi.list(), datasetApi.list(), collectionApi.tree(),
    ])
    rows.value = metricList
    models.value = modelList
    collections.value = flatten(tree)
    const focusId = typeof route.query.id === 'string' ? route.query.id : undefined
    if (focusId) {
      const hit = metricList.find((item) => String(item.id) === focusId)
      if (hit) open(hit)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metric.loadFailed'))
  }
}

function open(row?: Metric) {
  if (row) {
    const copy = JSON.parse(JSON.stringify(row)) as Metric
    let field = ''
    try {
      field = (JSON.parse(copy.expressionJson || '{}') as { field?: string }).field || ''
    } catch {
      field = ''
    }
    Object.assign(form, { ...copy, field })
  } else {
    Object.assign(form, {
      name: '', description: '', modelId: '', expressionJson: '{"field":""}', aggregation: 'SUM',
      collectionId: collections.value[0]?.id || '', field: '',
    })
  }
  editingId.value = row?.id
  visible.value = true
}

watch(() => form.modelId, () => {
  if (!expressionField.value) return
  const exists = modelFields.value.some((field: DatasetField) => field.name === expressionField.value)
  if (!exists) expressionField.value = ''
})

async function save() {
  if (!(await validateForm(formRef.value))) return
  const payload = {
    name: form.name,
    description: form.description,
    modelId: form.modelId,
    expressionJson: JSON.stringify({ field: form.field || '' }),
    aggregation: form.aggregation,
    collectionId: form.collectionId,
  }
  try {
    if (editingId.value !== undefined) await metricApi.update(editingId.value, payload)
    else await metricApi.create(payload)
    visible.value = false
    ElMessage.success(t('metric.saved'))
    await load()
    if (route.query.id) await router.replace({ path: '/metrics' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('metric.moveToTrash'), t('common.deleteConfirmTitle'))
    await metricApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

function modelName(id: Id) {
  return models.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('metric.title') }}</h1>
      <el-button v-if="userStore.hasPermission('dataset:create')" type="primary" @click="open()">{{ t('metric.create') }}</el-button>
    </div>
    <el-table :data="rows" :empty-text="t('metric.empty')">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column :label="t('metric.model')"><template #default="{ row }">{{ modelName(row.modelId) }}</template></el-table-column>
      <el-table-column :label="t('metric.aggregation')" width="100">
        <template #default="{ row }">{{ displayLabel(row.aggregation) }}</template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
      <el-table-column :label="t('common.actions')" width="160">
        <template #default="{ row }">
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editingId === undefined ? t('metric.createTitle') : t('metric.editTitle')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="t('metric.model')" prop="modelId">
          <el-select v-model="form.modelId" class="full-width" filterable :placeholder="t('metric.selectModel')">
            <el-option v-for="item in models" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('metric.refField')" prop="field">
          <el-select v-model="expressionField" class="full-width" filterable :placeholder="t('metric.selectModelField')">
            <el-option
              v-for="field in modelFields"
              :key="field.name"
              :label="`${field.name}（${displayLabel(field.fieldType)}）`"
              :value="field.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('metric.aggregation')" prop="aggregation">
          <el-select v-model="form.aggregation" class="full-width" :placeholder="t('metric.selectAgg')">
            <el-option v-for="agg in aggregationOptions" :key="agg" :label="displayLabel(agg)" :value="agg" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.collection')">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
