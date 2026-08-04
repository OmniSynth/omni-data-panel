<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const router = useRouter()
const route = useRoute()

async function submit() {
  if (!form.username || !form.password) return ElMessage.warning(t('login.needCredentials'))
  loading.value = true
  try {
    await useUserStore().login(form.username, form.password)
    await router.replace(String(route.query.redirect || '/'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('login.failed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="lang-pos">
      <ThemeSwitcher />
      <LanguageSwitcher />
    </div>
    <el-card class="login-card" shadow="never">
      <div class="brand">
        <img class="brand-logo" src="/favicon.png" alt="" width="48" height="48" />
        <h1>{{ t('login.title') }}</h1>
        <p class="subtitle">{{ t('login.subtitle') }}</p>
      </div>
      <el-form label-position="top" @keyup.enter="submit">
        <el-form-item :label="t('login.username')"><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item :label="t('login.password')"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-button type="primary" class="full-width" :loading="loading" @click="submit">{{ t('login.submit') }}</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--omni-space-4);
  background:
    radial-gradient(1200px 600px at 20% 10%, rgba(80, 158, 227, 0.28), transparent 55%),
    radial-gradient(900px 500px at 85% 80%, rgba(29, 79, 145, 0.22), transparent 50%),
    linear-gradient(160deg, #1a2740 0%, #243b5c 45%, #315b96 100%);
}
.lang-pos {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.login-card {
  width: 420px;
  max-width: 100%;
  padding: 10px 8px 6px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 14px;
  background: var(--omni-card);
  box-shadow: var(--omni-shadow), 0 24px 48px rgba(15, 23, 42, 0.18);
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin: 0 0 22px;
}
.brand-logo {
  width: 48px;
  height: 48px;
  object-fit: contain;
  display: block;
}
h1 {
  text-align: center;
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}
.subtitle {
  margin: 0;
  color: var(--omni-muted);
  font-size: 13px;
  text-align: center;
}
.full-width { width: 100%; }
</style>
