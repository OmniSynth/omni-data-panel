<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'

withDefaults(defineProps<{ size?: 'small' | 'default' }>(), { size: 'default' })
const { t } = useI18n()
const themeStore = useThemeStore()

const tip = computed(() =>
  themeStore.isDark ? t('theme.switchToLight') : t('theme.switchToDark'))
</script>

<template>
  <el-tooltip :content="tip" placement="bottom" :show-after="400">
    <el-button
      class="theme-switcher"
      :size="size"
      plain
      :aria-label="tip"
      @click="themeStore.toggle()"
    >
      <!-- 夜间：显示太阳（点一下切回普通）；普通：显示月亮 -->
      <svg v-if="themeStore.isDark" class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="4" fill="currentColor" />
        <path
          fill="currentColor"
          d="M12 2.5a1 1 0 0 1 1 1V5a1 1 0 1 1-2 0V3.5a1 1 0 0 1 1-1zm0 14a1 1 0 0 1 1 1v1.5a1 1 0 1 1-2 0V17.5a1 1 0 0 1 1-1zM4.22 4.22a1 1 0 0 1 1.42 0L6.7 5.3A1 1 0 1 1 5.3 6.7L4.22 5.64a1 1 0 0 1 0-1.42zm12.66 12.66a1 1 0 0 1 1.42 0l1.06 1.06a1 1 0 1 1-1.42 1.42L16.88 18.3a1 1 0 0 1 0-1.42zM2.5 12a1 1 0 0 1 1-1H5a1 1 0 1 1 0 2H3.5a1 1 0 0 1-1-1zm14 0a1 1 0 0 1 1-1h1.5a1 1 0 1 1 0 2H17.5a1 1 0 0 1-1-1zM4.22 19.78a1 1 0 0 1 0-1.42L5.3 17.3A1 1 0 1 1 6.7 18.7l-1.06 1.08a1 1 0 0 1-1.42 0zm12.66-12.66a1 1 0 0 1 0-1.42L17.94 4.64a1 1 0 1 1 1.42 1.42L18.3 7.12a1 1 0 0 1-1.42 0z"
        />
      </svg>
      <svg v-else class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path
          fill="currentColor"
          d="M12.1 2a1 1 0 0 1 .9 1.4 7.5 7.5 0 1 0 7.6 7.6 1 1 0 0 1 1.4.9A9.5 9.5 0 1 1 12.1 2z"
        />
      </svg>
    </el-button>
  </el-tooltip>
</template>

<style scoped>
.theme-switcher {
  padding: 8px;
  min-width: 32px;
}
.icon {
  width: 16px;
  height: 16px;
  display: block;
}
</style>
