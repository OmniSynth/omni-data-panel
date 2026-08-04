<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox, promptBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, collectionApi, dashboardApi, datasetApi, metricApi } from '@/api'
import { formatDateTime } from '@/display'
import { resourcePath, resourceTypeLabel } from '@/nav'
import type { Collection, CollectionItem, Id, ResourceType } from '@/types'

const { t } = useI18n()
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
const isPersonalRoot = computed(() => collection.value?.personalOwnerId != null)

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
    ElMessage.error(error instanceof Error ? error.message : t('collection.loadFailed'))
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
    const { value } = await promptBox(t('collection.renamePrompt'), t('collection.renameTitle'), {
      inputValue: collection.value.name,
      inputPattern: /\S+/,
      inputErrorMessage: t('common.nameRequired'),
    })
    await collectionApi.update(collection.value.id, {
      name: value,
      description: collection.value.description,
      parentId: collection.value.parentId,
    })
    ElMessage.success(t('common.updated'))
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.updateFailed'))
  }
}

async function removeCollection() {
  if (!collection.value || isPersonalRoot.value) return
  try {
    await confirmBox(t('collection.deleteConfirm'), t('common.deleteConfirmTitle'), { type: 'warning' })
    await collectionApi.remove(collection.value.id)
    ElMessage.success(t('collection.deleted'))
    await router.push('/')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

async function removeItem(row: CollectionItem) {
  if (row.type === 'COLLECTION') return
  try {
    await confirmBox(t('collection.deleteItemConfirm'), t('common.deleteConfirmTitle'), { type: 'warning' })
    switch (row.type) {
      case 'QUESTION':
        await chartApi.remove(row.id)
        break
      case 'DASHBOARD':
        await dashboardApi.remove(row.id)
        break
      case 'MODEL':
        await datasetApi.remove(row.id)
        break
      case 'METRIC':
        await metricApi.remove(row.id)
        break
      default:
        throw new Error(t('common.deleteFailed'))
    }
    ElMessage.success(t('collection.itemDeleted'))
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

function openMove(row: CollectionItem) {
  movingItem.value = row
  targetCollectionId.value = undefined
  moveVisible.value = true
}

async function submitMove() {
  if (!movingItem.value || targetCollectionId.value === undefined) return ElMessage.warning(t('collection.needTarget'))
  try {
    await collectionApi.move({
      resourceType: movingItem.value.type as ResourceType,
      resourceId: movingItem.value.id,
      collectionId: targetCollectionId.value,
    })
    moveVisible.value = false
    ElMessage.success(t('collection.moved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('collection.moveFailed'))
  }
}

watch(collectionId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ collection?.name || t('collection.title') }}</h1>
        <p class="muted">{{ collection?.description || t('collection.subtitle') }}</p>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button @click="rename">{{ t('collection.rename') }}</el-button>
        <el-button
          v-if="!isPersonalRoot"
          type="danger"
          plain
          @click="removeCollection"
        >{{ t('collection.deleteCollection') }}</el-button>
      </div>
    </div>
    <el-table :data="items" :empty-text="t('collection.empty')">
      <el-table-column prop="name" :label="t('common.name')" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItem(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.type')" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" min-width="220" show-overflow-tooltip />
      <el-table-column :label="t('collection.updatedAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="220">
        <template #default="{ row }">
          <el-button v-if="row.type !== 'COLLECTION'" link @click="openMove(row)">{{ t('collection.moveTo') }}</el-button>
          <el-button
            v-if="row.type !== 'COLLECTION'"
            link
            type="danger"
            @click="removeItem(row)"
          >{{ t('common.delete') }}</el-button>
          <el-button link type="primary" @click="openItem(row)">{{ t('common.open') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="moveVisible" :title="t('collection.moveDialogTitle')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="t('collection.target')">
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
        <el-button @click="moveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitMove">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
