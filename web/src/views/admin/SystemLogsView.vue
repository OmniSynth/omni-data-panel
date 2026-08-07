<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { settingsApi, systemLogApi } from '@/api'
import { formatDateTime } from '@/display'
import type { SystemLogEntry, SystemLogMeta } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const clearing = ref(false)
const logsClearEnabled = ref(true)
const autoRefresh = ref(true)
const entries = ref<SystemLogEntry[]>([])
const meta = ref<SystemLogMeta>({ capacity: 2000, buffered: 0 })
const panelRef = ref<HTMLElement>()
const stickToBottom = ref(true)
let timer: ReturnType<typeof setInterval> | undefined
let loadingQuiet = false

const filters = reactive({
  keyword: '',
  level: '',
})

const filteredEntries = computed(() => {
  const q = filters.keyword.trim().toLowerCase()
  if (!q) return entries.value
  return entries.value.filter((entry) => {
    const haystack = [
      entry.message,
      entry.loggerName,
      entry.threadName,
      entry.stackTrace,
      entry.level,
      entry.requestId,
    ].join('\n').toLowerCase()
    return haystack.includes(q)
  })
})

const logText = computed(() => {
  if (!filteredEntries.value.length) return ''
  return [...filteredEntries.value].reverse().map(formatEntry).join('\n')
})

function formatEntry(entry: SystemLogEntry) {
  const time = formatDateTime(entry.createdAt) || '-'
  const level = (entry.level || 'INFO').padEnd(5, ' ')
  const logger = entry.loggerName || '-'
  const thread = entry.threadName ? ` [${entry.threadName}]` : ''
  const requestId = entry.requestId ? ` req=${entry.requestId}` : ''
  const lines = [`${time} ${level} ${logger}${thread}${requestId} ${entry.message || ''}`]
  if (entry.stackTrace) {
    for (const line of entry.stackTrace.split('\n')) {
      if (line.trim()) lines.push(`    ${line}`)
    }
  }
  return lines.join('\n')
}

function entryFingerprint(list: SystemLogEntry[]) {
  if (!list.length) return '0'
  const newest = list[0]
  return `${list.length}|${newest?.createdAt}|${newest?.message}|${newest?.level}`
}

async function loadMeta() {
  try {
    meta.value = await systemLogApi.meta()
  } catch {
    // 元信息失败不阻断主视图
  }
}

async function load(options?: { quiet?: boolean }) {
  const quiet = options?.quiet === true
  if (quiet) {
    if (loadingQuiet || loading.value) return
    loadingQuiet = true
  } else {
    loading.value = true
  }
  const before = entryFingerprint(entries.value)
  const nearBottom = stickToBottom.value
  try {
    await loadMeta()
    const result = await systemLogApi.page({
      level: filters.level || undefined,
      page: 1,
      size: meta.value.capacity || 2000,
    })
    entries.value = result.items
    if (nearBottom && entryFingerprint(result.items) !== before) {
      await nextTick()
      scrollToBottom()
    }
  } catch (error) {
    if (!quiet) {
      ElMessage.error(error instanceof Error ? error.message : t('systemLog.loadFailed'))
    }
  } finally {
    if (quiet) loadingQuiet = false
    else loading.value = false
  }
}

function scrollToBottom() {
  const el = panelRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function onPanelScroll() {
  const el = panelRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

function downloadLogs() {
  const text = logText.value || ''
  if (!text) {
    ElMessage.info(t('systemLog.empty'))
    return
  }
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `system-logs-${Date.now()}.log`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function clearLogs() {
  try {
    await confirmBox(t('systemLog.clearConfirm'), t('systemLog.clearTitle'), { type: 'warning' })
  } catch {
    return
  }
  clearing.value = true
  try {
    await systemLogApi.clear()
    ElMessage.success(t('systemLog.cleared'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('systemLog.clearFailed'))
  } finally {
    clearing.value = false
  }
}

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    load({ quiet: true })
  }, 2000)
}

function stopPolling() {
  if (timer !== undefined) {
    clearInterval(timer)
    timer = undefined
  }
}

watch(() => filters.level, () => load())
watch(autoRefresh, startPolling)

onMounted(async () => {
  logsClearEnabled.value = await settingsApi.logsClearEnabled()
  await load()
  stickToBottom.value = true
  await nextTick()
  scrollToBottom()
  startPolling()
})
onUnmounted(stopPolling)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('systemLog.title') }}</h1>
        <p class="hint">{{ t('systemLog.hint', { buffered: meta.buffered, capacity: meta.capacity }) }}</p>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.keyword"
        clearable
        :placeholder="t('systemLog.filterPlaceholder')"
        class="filter-input"
      />
      <el-select v-model="filters.level" clearable :placeholder="t('systemLog.level')" style="width: 140px">
        <el-option :label="t('systemLog.allLevels')" value="" />
        <el-option label="INFO" value="INFO" />
        <el-option label="WARN" value="WARN" />
        <el-option label="ERROR" value="ERROR" />
      </el-select>
      <el-switch
        v-model="autoRefresh"
        inline-prompt
        :active-text="t('systemLog.autoOn')"
        :inactive-text="t('systemLog.autoOff')"
      />
      <el-button :loading="loading" @click="load()">{{ t('common.refresh') }}</el-button>
      <el-button type="primary" @click="downloadLogs">{{ t('systemLog.download') }}</el-button>
      <el-button
        v-if="logsClearEnabled"
        type="danger"
        plain
        :loading="clearing"
        @click="clearLogs"
      >{{ t('systemLog.clear') }}</el-button>
    </div>

    <div ref="panelRef" v-loading="loading" class="log-panel" @scroll="onPanelScroll">
      <pre v-if="logText" class="log-stream">{{ logText }}</pre>
      <div v-else class="log-empty">{{ t('systemLog.empty') }}</div>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}
.filter-input {
  flex: 1;
  min-width: 220px;
  max-width: 420px;
}
.log-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  min-height: 480px;
  max-height: calc(100vh - 220px);
  overflow: auto;
}
.log-stream {
  margin: 0;
  padding: 14px 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-primary);
}
.log-empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
