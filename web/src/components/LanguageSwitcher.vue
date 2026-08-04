<script setup lang="ts">
import { currentLocale, setLocale, type AppLocale } from '@/i18n'

const props = withDefaults(defineProps<{ size?: 'small' | 'default' }>(), { size: 'default' })

/** 切换界面语言并写入 localStorage */
function onChange(value: string) {
  setLocale(value as AppLocale)
}
</script>

<template>
  <el-select
    :model-value="currentLocale"
    class="lang-switcher"
    :size="props.size"
    @update:model-value="onChange"
  >
    <el-option label="中文" value="zh-CN" />
    <el-option label="English" value="en-US" />
  </el-select>
</template>

<style scoped>
.lang-switcher {
  width: 118px;
}
</style>
