<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingsApi } from '@/api'
import type { SiteSettings } from '@/types'

const loading = ref(false)
const saving = ref(false)
const form = ref({ siteName: '全域数据分析', embedEnabled: false })

async function load() {
  loading.value = true
  try {
    const settings = await settingsApi.get()
    form.value = {
      siteName: String(settings['site.name'] || '全域数据分析'),
      embedEnabled: String(settings['embed.enabled']) === 'true' || settings['embed.enabled'] === true,
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload: SiteSettings = {
      'site.name': form.value.siteName.trim() || '全域数据分析',
      'embed.enabled': form.value.embedEnabled ? 'true' : 'false',
    }
    await settingsApi.update(payload)
    ElMessage.success('设置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header"><h1 class="page-title">通用设置</h1></div>
    <el-card style="max-width:560px">
      <el-form label-width="110px">
        <el-form-item label="站点名称">
          <el-input v-model="form.siteName" placeholder="显示在分析壳顶栏" />
        </el-form-item>
        <el-form-item label="允许嵌入">
          <el-switch v-model="form.embedEnabled" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
