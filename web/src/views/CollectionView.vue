<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi } from '@/api'
import { formatDateTime } from '@/display'
import { resourcePath, resourceTypeLabel } from '@/nav'
import type { Collection, CollectionItem, Id, ResourceType } from '@/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const collection = ref<Collection>()
const items = ref<CollectionItem[]>([])
const tree = ref<Collection[]>([])
const moveVisible = ref(false)
const movingItem = ref<CollectionItem>()
const targetCollectionId = ref<Id>()

const collectionId = computed(() => String(route.params.id))

function flatten(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flatten(node.children, acc)
  }
  return acc
}

const flatCollections = computed(() => flatten(tree.value))

function findCollection(nodes: Collection[], id: string): Collection | undefined {
  for (const node of nodes) {
    if (String(node.id) === id) return node
    if (node.children?.length) {
      const hit = findCollection(node.children, id)
      if (hit) return hit
    }
  }
  return undefined
}

async function load() {
  loading.value = true
  try {
    tree.value = await collectionApi.tree()
    collection.value = findCollection(tree.value, collectionId.value)
    items.value = await collectionApi.items(collectionId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '集合加载失败')
  } finally {
    loading.value = false
  }
}

function openItem(row: CollectionItem) {
  if (row.type === 'COLLECTION') router.push(`/collections/${row.id}`)
  else router.push(resourcePath(row.type, row.id))
}

async function rename() {
  if (!collection.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入集合名称', '重命名集合', {
      inputValue: collection.value.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    await collectionApi.update(collection.value.id, {
      name: value,
      description: collection.value.description,
      parentId: collection.value.parentId,
    })
    ElMessage.success('已更新')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '更新失败')
  }
}

async function removeCollection() {
  if (!collection.value) return
  try {
    await ElMessageBox.confirm('确认删除该集合？集合需为空才可删除。', '删除确认', { type: 'warning' })
    await collectionApi.remove(collection.value.id)
    ElMessage.success('集合已删除')
    await router.push('/')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function openMove(row: CollectionItem) {
  movingItem.value = row
  targetCollectionId.value = undefined
  moveVisible.value = true
}

async function submitMove() {
  if (!movingItem.value || targetCollectionId.value === undefined) return ElMessage.warning('请选择目标集合')
  try {
    await collectionApi.move({
      resourceType: movingItem.value.type as ResourceType,
      resourceId: movingItem.value.id,
      collectionId: targetCollectionId.value,
    })
    moveVisible.value = false
    ElMessage.success('已移入目标集合')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移动失败')
  }
}

watch(collectionId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ collection?.name || '集合' }}</h1>
        <p class="muted">{{ collection?.description || '浏览并管理集合中的内容' }}</p>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button @click="rename">重命名</el-button>
        <el-button type="danger" plain @click="removeCollection">删除集合</el-button>
      </div>
    </div>
    <el-table :data="items" empty-text="该集合暂无内容">
      <el-table-column prop="name" label="名称" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItem(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-if="row.type !== 'COLLECTION'" link @click="openMove(row)">移入</el-button>
          <el-button link type="primary" @click="openItem(row)">打开</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="moveVisible" title="移入集合" width="420px">
      <el-form label-width="90px">
        <el-form-item label="目标集合">
          <el-select v-model="targetCollectionId" class="full-width" filterable>
            <el-option
              v-for="item in flatCollections"
              :key="item.id"
              :label="item.name"
              :value="item.id"
              :disabled="String(item.id) === collectionId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMove">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>
