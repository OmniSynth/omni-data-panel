<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import { elementLocale } from '@/i18n'
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <router-view />
  </ElConfigProvider>
</template>
