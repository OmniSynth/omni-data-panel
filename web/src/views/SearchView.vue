<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api'
import { resourcePath, resourceTypeLabel } from '@/nav'
import type { SearchHit } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const hits = ref<SearchHit[]>([])

async function load() {
  const q = keyword.value.trim()
  if (!q) {
    hits.value = []
    return
  }
  loading.value = true
  try {
    hits.value = await searchApi.search(q)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('search.failed'))
  } finally {
    loading.value = false
  }
}

function open(hit: SearchHit) {
  router.push(resourcePath(hit.resourceType, hit.resourceId))
}

watch(() => route.query.q, (value) => {
  keyword.value = typeof value === 'string' ? value : ''
  load()
})

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('search.title') }}</h1>
      <el-input v-model="keyword" style="width:320px" clearable :placeholder="t('search.placeholder')" @keyup.enter="load">
        <template #append><el-button @click="load">{{ t('common.search') }}</el-button></template>
      </el-input>
    </div>
    <el-table v-loading="loading" :data="hits" :empty-text="t('search.empty')">
      <el-table-column prop="name" :label="t('common.name')" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.type')" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
    </el-table>
  </div>
</template>
