<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { requiredRule, validateForm } from '@/form/rules'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const form = reactive({ username: '', password: '' })
const formRef = ref<FormInstance>()
const loading = ref(false)
const router = useRouter()
const route = useRoute()

const formRules = computed<FormRules>(() => ({
  username: [requiredRule(t('common.pleaseEnter', { field: t('login.username') }))],
  password: [requiredRule(t('common.pleaseEnter', { field: t('login.password') }))],
}))

async function submit() {
  if (!(await validateForm(formRef.value))) return
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
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" @keyup.enter="submit">
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item :label="t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
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
  text-align: center;
  margin-bottom: 18px;
}
.brand-logo {
  display: block;
  margin: 0 auto 10px;
  border-radius: 10px;
}
.brand h1 {
  margin: 0;
  font-size: 22px;
  color: var(--omni-text);
}
.subtitle {
  margin: 6px 0 0;
  color: var(--omni-muted);
  font-size: 13px;
}
.full-width { width: 100%; }
</style>
