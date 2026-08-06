<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { authApi, publicApi } from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import FontSizeSwitcher from '@/components/FontSizeSwitcher.vue'
import { requiredRule, validateForm } from '@/form/rules'
import { applyDocumentTitle } from '@/siteBranding'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const form = reactive({ username: '', password: '' })
const mfaForm = reactive({ code: '' })
const formRef = ref<FormInstance>()
const mfaFormRef = ref<FormInstance>()
const loading = ref(false)
const step = ref<'password' | 'mfa'>('password')
const siteTitle = ref(t('login.title'))
const ssoEnabled = ref(false)
const ssoName = ref(t('login.sso'))
const ssoUrl = ref('')
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

const highlights = computed(() => [
  t('login.highlightQuery'),
  t('login.highlightVisual'),
  t('login.highlightShare'),
])

onMounted(async () => {
  const ssoError = typeof route.query.ssoError === 'string' ? route.query.ssoError : ''
  if (ssoError) {
    ElMessage.error(ssoError)
  }
  try {
    const site = await publicApi.site()
    const name = String(site['site.name'] || '').trim()
    if (name) {
      siteTitle.value = name
      applyDocumentTitle(name)
    }
  } catch {
    applyDocumentTitle(siteTitle.value)
  }
  try {
    const status = await authApi.oidcStatus()
    ssoEnabled.value = !!status.enabled && !!status.authorizationUrl
    ssoName.value = status.clientName || t('login.sso')
    ssoUrl.value = status.authorizationUrl || ''
  } catch {
    ssoEnabled.value = false
  }
})

function startSso() {
  if (!ssoUrl.value) return
  window.location.href = ssoUrl.value
}

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
    <aside class="showcase" aria-hidden="false">
      <div class="showcase-glow" />
      <div class="showcase-grid" />
      <div class="showcase-inner">
        <div class="showcase-brand">
          <img class="showcase-logo" src="/favicon.png" alt="" width="56" height="56" />
          <p class="eyebrow">Omni Data Panel</p>
        </div>
        <h1 class="showcase-title">{{ siteTitle }}</h1>
        <p class="showcase-subtitle">{{ t('login.subtitle') }}</p>
        <ul class="highlights">
          <li v-for="item in highlights" :key="item">{{ item }}</li>
        </ul>
      </div>
      <svg class="chart-motif" viewBox="0 0 480 220" fill="none" aria-hidden="true">
        <path
          d="M24 168 L88 132 L152 148 L216 96 L280 112 L344 64 L408 88 L456 48"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
          opacity="0.55"
        />
        <path
          d="M24 168 L88 132 L152 148 L216 96 L280 112 L344 64 L408 88 L456 48 V196 H24 Z"
          fill="url(#loginArea)"
          opacity="0.35"
        />
        <circle cx="216" cy="96" r="4" fill="currentColor" opacity="0.8" />
        <circle cx="344" cy="64" r="4" fill="currentColor" opacity="0.8" />
        <defs>
          <linearGradient id="loginArea" x1="24" y1="48" x2="24" y2="196" gradientUnits="userSpaceOnUse">
            <stop stop-color="#ffffff" stop-opacity="0.35" />
            <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
          </linearGradient>
        </defs>
      </svg>
    </aside>

    <section class="panel">
      <div class="panel-tools">
        <ThemeSwitcher />
        <FontSizeSwitcher />
        <LanguageSwitcher />
      </div>
      <div class="panel-card">
        <header class="panel-head">
          <h2>{{ step === 'mfa' ? t('login.mfaWelcome') : t('login.welcome') }}</h2>
          <p>{{ step === 'mfa' ? t('login.mfaSubtitle') : t('login.formHint') }}</p>
        </header>

        <el-form
          v-if="step === 'password'"
          ref="formRef"
          class="panel-form"
          :model="form"
          :rules="formRules"
          label-position="top"
          size="large"
          @keyup.enter="submitPassword"
        >
          <el-form-item :label="t('login.username')" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              :placeholder="t('login.usernamePlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('login.password')" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              :placeholder="t('login.passwordPlaceholder')"
            />
          </el-form-item>
          <div class="panel-actions">
            <el-button type="primary" class="submit" :loading="loading" @click="submitPassword">
              {{ t('login.submit') }}
            </el-button>
            <template v-if="ssoEnabled">
              <div class="sso-divider"><span>{{ t('login.ssoOr') }}</span></div>
              <el-button class="submit ghost" :disabled="loading" @click="startSso">
                {{ ssoName }}
              </el-button>
            </template>
          </div>
        </el-form>

        <el-form
          v-else
          ref="mfaFormRef"
          class="panel-form"
          :model="mfaForm"
          :rules="mfaRules"
          label-position="top"
          size="large"
          @keyup.enter="submitMfa"
        >
          <el-form-item :label="t('login.mfaCode')" prop="code">
            <el-input
              v-model="mfaForm.code"
              autocomplete="one-time-code"
              :placeholder="t('login.mfaPlaceholder')"
            />
          </el-form-item>
          <div class="panel-actions">
            <el-button type="primary" class="submit" :loading="loading" @click="submitMfa">
              {{ t('login.mfaSubmit') }}
            </el-button>
            <el-button class="submit ghost" :disabled="loading" @click="backToPassword">
              {{ t('login.mfaBack') }}
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(380px, 0.85fr);
  min-height: 100vh;
  min-width: 0;
  background: var(--omni-bg);
  color: var(--omni-text);
  animation: loginFade 480ms ease-out;
}

.showcase {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px 36px;
  color: #f4f8fc;
  background:
    linear-gradient(155deg, #163a63 0%, #1d4f91 42%, #3d87c9 100%);
}

.showcase-glow {
  position: absolute;
  inset: -20% auto auto -10%;
  width: 70%;
  height: 70%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18), transparent 68%);
  pointer-events: none;
}

.showcase-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 85%);
  pointer-events: none;
}

.showcase-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
  animation: loginRise 560ms cubic-bezier(0.22, 1, 0.36, 1);
}

.showcase-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 36px;
}

.showcase-logo {
  border-radius: 14px;
  box-shadow: 0 10px 28px rgba(8, 28, 58, 0.28);
}

.eyebrow {
  margin: 0;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(244, 248, 252, 0.72);
}

.showcase-title {
  margin: 0;
  font-size: clamp(36px, 4.2vw, 52px);
  line-height: 1.12;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.showcase-subtitle {
  margin: 16px 0 0;
  max-width: 28em;
  font-size: 17px;
  line-height: 1.55;
  color: rgba(244, 248, 252, 0.82);
}

.highlights {
  margin: 36px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.highlights li {
  position: relative;
  padding-left: 22px;
  font-size: 14px;
  color: rgba(244, 248, 252, 0.88);
}

.highlights li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55em;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9fd0f5;
  box-shadow: 0 0 0 4px rgba(159, 208, 245, 0.18);
}

.chart-motif {
  position: relative;
  z-index: 1;
  width: min(100%, 520px);
  margin-top: 48px;
  color: #ffffff;
  opacity: 0.9;
  animation: loginRise 720ms cubic-bezier(0.22, 1, 0.36, 1);
}

.panel {
  position: relative;
  display: grid;
  place-items: center;
  padding: 48px 40px;
  background: var(--omni-bg);
}

.panel-tools {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.panel-card {
  width: min(100%, 400px);
  animation: loginRise 600ms cubic-bezier(0.22, 1, 0.36, 1);
}

.panel-head {
  margin-bottom: 28px;
}

.panel-head h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--omni-text);
}

.panel-head p {
  margin: 8px 0 0;
  color: var(--omni-muted);
  font-size: 14px;
  line-height: 1.5;
}

.panel-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.panel-form :deep(.el-form-item__label) {
  margin-bottom: 6px !important;
  line-height: 1.4;
  justify-content: flex-start;
  color: var(--omni-text);
  font-weight: 500;
}

.panel-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--omni-border) inset;
  padding: 4px 14px;
  transition: box-shadow 160ms ease;
}

.panel-form :deep(.el-input__wrapper:hover),
.panel-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--omni-accent) inset;
}

.panel-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.panel-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.sso-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.sso-divider::before,
.sso-divider::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--omni-border);
}

.submit {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-weight: 600;
}

.submit.ghost {
  --el-button-bg-color: transparent;
  --el-button-border-color: var(--omni-border);
}

@keyframes loginFade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes loginRise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 960px) {
  .login {
    grid-template-columns: 1fr;
  }
  .showcase {
    min-height: 280px;
    padding: 32px 28px 24px;
  }
  .showcase-title {
    font-size: 32px;
  }
  .chart-motif {
    display: none;
  }
  .panel {
    padding: 32px 24px 48px;
  }
}
</style>
