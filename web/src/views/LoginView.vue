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
const mfaForm = reactive({ code: '' })
const formRef = ref<FormInstance>()
const mfaFormRef = ref<FormInstance>()
const loading = ref(false)
const step = ref<'password' | 'mfa'>('password')
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRules = computed<FormRules>(() => ({
  username: [requiredRule(t('common.pleaseEnter', { field: t('login.username') }))],
  password: [requiredRule(t('common.pleaseEnter', { field: t('login.password') }))],
}))

const mfaRules = computed<FormRules>(() => ({
  code: [requiredRule(t('common.pleaseEnter', { field: t('login.mfaCode') }))],
}))

async function submitPassword() {
  if (!(await validateForm(formRef.value))) return
  loading.value = true
  try {
    const result = await userStore.login(form.username, form.password)
    if (result === 'mfa') {
      step.value = 'mfa'
      mfaForm.code = ''
      return
    }
    await router.replace(String(route.query.redirect || '/'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('login.failed'))
  } finally {
    loading.value = false
  }
}

async function submitMfa() {
  if (!(await validateForm(mfaFormRef.value))) return
  loading.value = true
  try {
    await userStore.verifyMfa(mfaForm.code.trim())
    await router.replace(String(route.query.redirect || '/'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('login.mfaFailed'))
  } finally {
    loading.value = false
  }
}

function backToPassword() {
  userStore.clearMfaChallenge()
  step.value = 'password'
  mfaForm.code = ''
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
        <p class="subtitle">{{ step === 'mfa' ? t('login.mfaSubtitle') : t('login.subtitle') }}</p>
      </div>
      <el-form
        v-if="step === 'password'"
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @keyup.enter="submitPassword"
      >
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item :label="t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <div class="login-actions">
          <el-button type="primary" class="full-width" :loading="loading" @click="submitPassword">
            {{ t('login.submit') }}
          </el-button>
        </div>
      </el-form>
      <el-form
        v-else
        ref="mfaFormRef"
        :model="mfaForm"
        :rules="mfaRules"
        label-position="top"
        @keyup.enter="submitMfa"
      >
        <el-form-item :label="t('login.mfaCode')" prop="code">
          <el-input v-model="mfaForm.code" autocomplete="one-time-code" :placeholder="t('login.mfaPlaceholder')" />
        </el-form-item>
        <div class="login-actions">
          <el-button type="primary" class="full-width" :loading="loading" @click="submitMfa">
            {{ t('login.mfaSubmit') }}
          </el-button>
          <el-button class="full-width" :disabled="loading" @click="backToPassword">
            {{ t('login.mfaBack') }}
          </el-button>
        </div>
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
.login-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.login-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  margin-bottom: 6px !important;
  line-height: 1.4;
  justify-content: flex-start;
}
:deep(.el-input),
:deep(.el-input__wrapper) {
  width: 100%;
}
</style>
