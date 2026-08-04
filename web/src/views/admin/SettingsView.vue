<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { settingsApi } from '@/api'
import type { SiteSettings } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const form = ref({
  siteName: '',
  embedEnabled: false,
  queryCacheEnabled: false,
  queryCacheTtlSeconds: 300,
})

async function load() {
  loading.value = true
  try {
    const settings = await settingsApi.get()
    const ttl = Number(settings['cache.query.ttl-seconds'])
    form.value = {
      siteName: String(settings['site.name'] || t('settings.defaultSiteName')),
      embedEnabled: String(settings['embed.enabled']) === 'true' || settings['embed.enabled'] === true,
      queryCacheEnabled: String(settings['cache.query.enabled']) === 'true'
        || settings['cache.query.enabled'] === true,
      queryCacheTtlSeconds: Number.isFinite(ttl) && ttl > 0 ? ttl : 300,
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('settings.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload: SiteSettings = {
      'site.name': form.value.siteName.trim() || t('settings.defaultSiteName'),
      'embed.enabled': form.value.embedEnabled ? 'true' : 'false',
      'cache.query.enabled': form.value.queryCacheEnabled ? 'true' : 'false',
      'cache.query.ttl-seconds': String(form.value.queryCacheTtlSeconds),
    }
    await settingsApi.update(payload)
    ElMessage.success(t('settings.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header"><h1 class="page-title">{{ t('settings.title') }}</h1></div>
    <el-card style="max-width:560px">
      <el-form label-width="130px">
        <el-form-item :label="t('settings.siteName')">
          <el-input v-model="form.siteName" :placeholder="t('settings.siteNameHint')" />
        </el-form-item>
        <el-form-item :label="t('settings.embedEnabled')">
          <el-switch v-model="form.embedEnabled" />
        </el-form-item>
        <el-form-item :label="t('settings.queryCache')">
          <el-switch v-model="form.queryCacheEnabled" />
        </el-form-item>
        <el-form-item :label="t('settings.cacheTtl')">
          <el-input-number
            v-model="form.queryCacheTtlSeconds"
            :min="30"
            :max="86400"
            :disabled="!form.queryCacheEnabled"
            controls-position="right"
          />
          <div class="hint">{{ t('settings.cacheHint') }}</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
</style>
