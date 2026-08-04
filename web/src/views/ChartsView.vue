<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { chartApi, collectionApi, publicLinkApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, Id, QueryResult, QuerySubmission } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const userStore = useUserStore()
const router = useRouter()
const rows = ref<Chart[]>([])
const collections = ref<Collection[]>([])
const visible = ref(false)
const preview = ref<Chart>()
const previewResult = ref<QueryResult>()
const form = reactive<Chart>({
  id: '', name: '', datasetId: '', queryJson: '{}', chartType: 'bar', configJson: '{}',
  description: '', collectionId: '',
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
    const [chartList, tree] = await Promise.all([chartApi.list(), collectionApi.tree()])
    rows.value = chartList
    collections.value = flatten(tree)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问题加载失败')
  }
}

function edit(row: Chart) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  visible.value = true
}

async function save() {
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
    ElMessage.success('问题已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function remove(id: Id) {
  try {
    await ElMessageBox.confirm('确认将该问题移入废纸篓？', '删除确认')
    await chartApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function showPreview(chart: Chart) {
  preview.value = chart
  previewResult.value = undefined
  try {
    const { queryId } = await queryApi.submit(JSON.parse(chart.queryJson) as QuerySubmission)
    while (true) {
      const snapshot = await queryApi.status(queryId)
      if (snapshot.status === 'SUCCEEDED') {
        previewResult.value = snapshot.result
        return
      }
      if (snapshot.status === 'FAILED' || snapshot.status === 'CANCELLED') {
        throw new Error(snapshot.error || '问题查询未成功')
      }
      await new Promise((resolve) => window.setTimeout(resolve, 500))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问题预览失败')
  }
}

async function share(chart: Chart) {
  try {
    const link = await publicLinkApi.create({ resourceType: 'QUESTION', resourceId: chart.id })
    await navigator.clipboard.writeText(`${location.origin}/public/question/${link.token}`)
    ElMessage.success('公开链接已复制')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建公开链接失败')
  }
}

function collectionName(id?: Id) {
  if (id === undefined || id === null || id === '') return '-'
  return collections.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">问题</h1>
      <el-button v-if="userStore.hasPermission('chart:create')" type="primary" @click="router.push('/query')">新建问题</el-button>
    </div>
    <el-table :data="rows" empty-text="暂无问题">
      <el-table-column prop="name" label="名称" min-width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/questions/${row.id}`)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ displayLabel(row.chartType) }}</template>
      </el-table-column>
      <el-table-column label="集合" width="160">
        <template #default="{ row }">{{ collectionName(row.collectionId) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link @click="showPreview(row)">预览</el-button>
          <el-button link @click="edit(row)">编辑</el-button>
          <el-button link type="primary" @click="share(row)">分享</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="编辑问题" width="560px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.chartType" class="full-width">
            <el-option label="表格" value="table" />
            <el-option label="柱状图" value="bar" />
            <el-option label="折线图" value="line" />
            <el-option label="饼图" value="pie" />
          </el-select>
        </el-form-item>
        <el-form-item label="集合">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :model-value="!!preview" title="问题预览" width="800px" @close="preview=undefined">
      <ChartPreview
        v-if="preview"
        :type="preview.chartType"
        :result="previewResult"
        :option="JSON.parse(preview.configJson || '{}')"
      />
    </el-dialog>
  </div>
</template>
