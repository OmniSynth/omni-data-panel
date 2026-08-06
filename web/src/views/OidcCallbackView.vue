<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = ref(t('login.ssoExchanging'))

onMounted(async () => {
  const error = typeof route.query.error === 'string' ? route.query.error : ''
  if (error) {
    message.value = error
    ElMessage.error(error)
    await router.replace({ path: '/login', query: { ssoError: error } })
    return
  }
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) {
    message.value = t('login.ssoFailed')
    ElMessage.error(t('login.ssoFailed'))
    await router.replace('/login')
    return
  }
  try {
    await userStore.completeOidcLogin(code)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect || '/')
  } catch (err) {
    const text = err instanceof Error ? err.message : t('login.ssoFailed')
    message.value = text
    ElMessage.error(text)
    await router.replace({ path: '/login', query: { ssoError: text } })
  }
})
</script>

<template>
  <div class="oidc-callback">
    <p>{{ message }}</p>
  </div>
</template>

<style scoped>
.oidc-callback {
  min-height: 100vh;
  display: grid;
  place-items: center;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color-page);
}
</style>
