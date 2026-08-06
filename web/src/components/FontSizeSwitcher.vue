<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFontSizeStore, type AppFontSize } from '@/stores/fontSize'

withDefaults(defineProps<{ size?: 'small' | 'default' }>(), { size: 'default' })
const { t } = useI18n()
const fontSizeStore = useFontSizeStore()

const tip = computed(() => t('fontSize.tip'))

/** 切换界面字号并写入 localStorage */
function onChange(value: string) {
  fontSizeStore.setFontSize(value as AppFontSize)
}
</script>

<template>
  <el-tooltip :content="tip" placement="bottom" :show-after="400">
    <el-select
      :model-value="fontSizeStore.fontSize"
      class="font-size-switcher"
      :size="size"
      :aria-label="tip"
      @update:model-value="onChange"
    >
      <el-option
        v-for="option in fontSizeStore.options"
        :key="option"
        :label="t(`fontSize.${option}`)"
        :value="option"
      />
    </el-select>
  </el-tooltip>
</template>

<style scoped>
.font-size-switcher {
  width: 100px;
}
</style>
