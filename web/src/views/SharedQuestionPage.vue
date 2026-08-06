<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { embedApi, publicApi } from '@/api'
import type { PublicQuestion } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const props = defineProps<{ mode: 'public' | 'embed' }>()
const { t } = useI18n()
const route = useRoute()
const loading = ref(false)
const question = ref<PublicQuestion>()

const token = computed(() => String(route.params.token || ''))

function chartOption(configJson: string) {
  try {
    return JSON.parse(configJson) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function load() {
  loading.value = true
  try {
    question.value = props.mode === 'public'
      ? await publicApi.question(token.value)
      : await embedApi.question(token.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('chart.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page standalone">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ question?.name || t('chart.title') }}</h1>
        <p class="muted">{{ question?.description || '' }}</p>
      </div>
    </div>
    <el-alert v-if="question?.error" :title="question.error" type="error" :closable="false" show-icon class="mb" />
    <el-card v-if="question">
      <ChartPreview
        :type="question.chartType"
        :result="question.result"
        :option="chartOption(question.configJson)"
        :fill="false"
      />
    </el-card>
  </div>
</template>

<style scoped>
.standalone { min-height: 100vh; background: #f5f6f8; }
.mb { margin-bottom: 14px; }
</style>
