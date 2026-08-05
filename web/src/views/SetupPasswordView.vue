<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { authApi } from '@/api'
import { minLengthRule, requiredRule, validateForm } from '@/form/rules'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance>()
const preview = ref<{ username: string; displayName: string; purpose: string }>()
const form = reactive({ password: '', confirm: '' })

const token = computed(() => String(route.query.token || ''))

const formRules = computed<FormRules>(() => ({
  password: [
    requiredRule(t('common.pleaseEnter', { field: t('setupPassword.password') })),
    minLengthRule(10, t('users.passwordMin')),
  ],
  confirm: [
    requiredRule(t('common.pleaseEnter', { field: t('setupPassword.confirm') })),
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error(t('setupPassword.mismatch')))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}))

const title = computed(() => {
  if (preview.value?.purpose === 'RESET_PASSWORD') return t('setupPassword.resetTitle')
  return t('setupPassword.activateTitle')
})

async function load() {
  loading.value = true
  try {
    if (!token.value) throw new Error(t('setupPassword.invalidLink'))
    preview.value = await authApi.previewSetupPassword(token.value)
  } catch (error) {
    preview.value = undefined
    ElMessage.error(error instanceof Error ? error.message : t('setupPassword.invalidLink'))
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!(await validateForm(formRef.value))) return
  saving.value = true
  try {
    await authApi.completeSetupPassword(token.value, form.password)
    ElMessage.success(t('setupPassword.success'))
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('setupPassword.failed'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="setup">
    <div class="lang-pos">
      <ThemeSwitcher />
      <LanguageSwitcher />
    </div>
    <el-card v-loading="loading" class="setup-card" shadow="never">
      <div class="brand">
        <img class="brand-logo" src="/favicon.png" alt="" width="48" height="48" />
        <h1>{{ title }}</h1>
        <p v-if="preview" class="subtitle">
          {{ t('setupPassword.account', { name: preview.displayName || preview.username }) }}
        </p>
      </div>
      <el-form
        v-if="preview"
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item :label="t('setupPassword.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="t('setupPassword.confirm')" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" class="full-width" :loading="saving" @click="submit">
          {{ t('setupPassword.submit') }}
        </el-button>
      </el-form>
      <el-empty v-else-if="!loading" :description="t('setupPassword.invalidLink')" />
      <div class="footer">
        <el-button link type="primary" @click="router.push('/login')">{{ t('setupPassword.backLogin') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.setup {
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
.setup-card {
  width: 420px;
  max-width: 100%;
  padding: 10px 8px 6px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 14px;
  background: var(--omni-card);
  box-shadow: var(--omni-shadow), 0 24px 48px rgba(15, 23, 42, 0.18);
}
.brand { text-align: center; margin-bottom: 18px; }
.brand-logo { display: block; margin: 0 auto 10px; border-radius: 10px; }
.brand h1 { margin: 0; font-size: 22px; color: var(--omni-text); }
.subtitle { margin: 6px 0 0; color: var(--omni-muted); font-size: 13px; }
.full-width { width: 100%; }
.footer { margin-top: 12px; text-align: center; }
</style>
