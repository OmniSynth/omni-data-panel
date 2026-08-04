<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi, datasetApi, metricApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Collection, Dataset, Id, Metric } from '@/types'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const rows = ref<Metric[]>([])
const models = ref<Dataset[]>([])
const collections = ref<Collection[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const form = reactive<Partial<Metric>>({
  name: '', description: '', modelId: '', expressionJson: '{"field":""}', aggregation: 'SUM', collectionId: '',
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
    ElMessage.error(error instanceof Error ? error.message : '指标加载失败')
  }
}

function open(row?: Metric) {
  Object.assign(form, row ? JSON.parse(JSON.stringify(row)) : {
    name: '', description: '', modelId: '', expressionJson: '{"field":""}', aggregation: 'SUM',
    collectionId: collections.value[0]?.id || '',
  })
  editingId.value = row?.id
  visible.value = true
}

async function save() {
  if (!form.name || !form.modelId || !form.aggregation) return ElMessage.warning('请完整填写指标')
  try {
    if (editingId.value !== undefined) await metricApi.update(editingId.value, form)
    else await metricApi.create(form)
    visible.value = false
    ElMessage.success('指标已保存')
    await load()
    if (route.query.id) await router.replace({ path: '/metrics' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function remove(id: Id) {
  try {
    await ElMessageBox.confirm('确认将该指标移入废纸篓？', '删除确认')
    await metricApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
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
      <h1 class="page-title">指标</h1>
      <el-button v-if="userStore.hasPermission('dataset:create')" type="primary" @click="open()">新建指标</el-button>
    </div>
    <el-table :data="rows" empty-text="暂无指标">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="模型"><template #default="{ row }">{{ modelName(row.modelId) }}</template></el-table-column>
      <el-table-column label="聚合" width="100">
        <template #default="{ row }">{{ displayLabel(row.aggregation) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editingId === undefined ? '新建指标' : '编辑指标'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.modelId" class="full-width" filterable placeholder="选择模型">
            <el-option v-for="item in models" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="聚合">
          <el-select v-model="form.aggregation" class="full-width" placeholder="选择聚合方式">
            <el-option label="求和" value="SUM" />
            <el-option label="平均" value="AVG" />
            <el-option label="计数" value="COUNT" />
            <el-option label="最大" value="MAX" />
            <el-option label="最小" value="MIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="表达式">
          <el-input v-model="form.expressionJson" type="textarea" :rows="3" placeholder='例如 {"field":"amount"}' />
        </el-form-item>
        <el-form-item label="集合">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
