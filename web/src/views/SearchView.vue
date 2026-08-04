<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api'
import { resourcePath, resourceTypeLabel } from '@/nav'
import type { SearchHit } from '@/types'

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
    ElMessage.error(error instanceof Error ? error.message : '搜索失败')
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
      <h1 class="page-title">搜索结果</h1>
      <el-input v-model="keyword" style="width:320px" clearable placeholder="输入关键词" @keyup.enter="load">
        <template #append><el-button @click="load">搜索</el-button></template>
      </el-input>
    </div>
    <el-table v-loading="loading" :data="hits" empty-text="没有匹配的内容">
      <el-table-column prop="name" label="名称" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
    </el-table>
  </div>
</template>
