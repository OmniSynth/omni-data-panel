<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { settingsApi } from '@/api'
import { emailRule, requiredRule, validateForm } from '@/form/rules'
import { applyDocumentTitle } from '@/siteBranding'
import type { SiteSettings } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const formRef = ref<FormInstance>()
const dirty = ref(false)
const passwordSet = ref(false)
const form = ref({
  siteName: '',
  embedEnabled: false,
  embedAllowedOrigins: '',
  sqlTipsCollapsedDefault: false,
  queryCacheEnabled: false,
  logsClearEnabled: true,
  queryCacheTtlSeconds: 300,
  maxConcurrentSessions: 2,
  mailHost: '',
  mailPort: 25,
  mailUsername: '',
  mailPassword: '',
  mailFrom: '',
  mailAuth: false,
  mailStartTls: false,
  mailTestTo: '',
})

const mailConfigured = computed(() => !!(form.value.mailHost.trim() || form.value.mailFrom.trim()))

const rules = computed<FormRules>(() => {
  if (!mailConfigured.value) return {}
  return {
    mailHost: [requiredRule(t('settings.mailHostRequired'))],
    mailFrom: [
      requiredRule(t('settings.mailFromRequired')),
      emailRule(t('settings.mailFromInvalid')),
    ],
  }
})

function asBool(value: unknown) {
  return String(value) === 'true' || value === true
}

function markDirty() {
  dirty.value = true
}

async function load() {
  loading.value = true
  try {
    const settings = await settingsApi.get()
    const ttl = Number(settings['cache.query.ttl-seconds'])
    const sessions = Number(settings['auth.session.max-concurrent'])
    const port = Number(settings['mail.port'])
    passwordSet.value = asBool(settings['mail.password.set'])
    form.value = {
      siteName: String(settings['site.name'] || t('settings.defaultSiteName')),
      embedEnabled: asBool(settings['embed.enabled']),
      embedAllowedOrigins: String(settings['embed.allowed-origins'] || ''),
      sqlTipsCollapsedDefault: asBool(settings['ui.sql.tips-collapsed-default']),
      queryCacheEnabled: asBool(settings['cache.query.enabled']),
      logsClearEnabled: settings['logs.clear.enabled'] == null
        ? true
        : asBool(settings['logs.clear.enabled']),
      queryCacheTtlSeconds: Number.isFinite(ttl) && ttl > 0 ? ttl : 300,
      maxConcurrentSessions: Number.isFinite(sessions) && sessions >= 0 ? sessions : 2,
      mailHost: String(settings['mail.host'] || ''),
      mailPort: Number.isFinite(port) && port > 0 ? port : 25,
      mailUsername: String(settings['mail.username'] || ''),
      mailPassword: '',
      mailFrom: String(settings['mail.from'] || ''),
      mailAuth: asBool(settings['mail.smtp.auth']),
      mailStartTls: asBool(settings['mail.smtp.starttls']),
      mailTestTo: String(settings['mail.from'] || ''),
    }
    dirty.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('settings.loadFailed'))
  } finally {
    loading.value = false
  }
}

function buildPayload(): SiteSettings {
  const payload: SiteSettings = {
    'site.name': form.value.siteName.trim() || t('settings.defaultSiteName'),
    'embed.enabled': form.value.embedEnabled ? 'true' : 'false',
    'embed.allowed-origins': form.value.embedAllowedOrigins.trim(),
    'ui.sql.tips-collapsed-default': form.value.sqlTipsCollapsedDefault ? 'true' : 'false',
    'cache.query.enabled': form.value.queryCacheEnabled ? 'true' : 'false',
    'cache.query.ttl-seconds': String(form.value.queryCacheTtlSeconds),
    'logs.clear.enabled': form.value.logsClearEnabled ? 'true' : 'false',
    'auth.session.max-concurrent': String(form.value.maxConcurrentSessions),
    'mail.host': form.value.mailHost.trim(),
    'mail.port': String(form.value.mailPort),
    'mail.username': form.value.mailUsername.trim(),
    'mail.from': form.value.mailFrom.trim(),
    'mail.smtp.auth': form.value.mailAuth ? 'true' : 'false',
    'mail.smtp.starttls': form.value.mailStartTls ? 'true' : 'false',
  }
  if (form.value.mailPassword.trim()) {
    payload['mail.password'] = form.value.mailPassword
  }
  return payload
}

async function save() {
  if (mailConfigured.value && !(await validateForm(formRef.value))) return
  saving.value = true
  try {
    const settings = await settingsApi.update(buildPayload())
    passwordSet.value = asBool(settings['mail.password.set'])
    form.value.mailPassword = ''
    dirty.value = false
    applyDocumentTitle(settings['site.name'] || form.value.siteName)
    ElMessage.success(t('settings.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function testMail() {
  if (dirty.value) {
    ElMessage.warning(t('settings.mailSaveFirst'))
    return
  }
  const to = form.value.mailTestTo.trim()
  if (!to) {
    ElMessage.error(t('settings.mailTestToRequired'))
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) {
    ElMessage.error(t('settings.mailTestToInvalid'))
    return
  }
  testing.value = true
  try {
    await settingsApi.testMail(to)
    ElMessage.success(t('settings.mailTestSent'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header"><h1 class="page-title">{{ t('settings.title') }}</h1></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" class="settings-form" @change="markDirty">
      <el-card>
        <el-form-item :label="t('settings.siteName')">
          <el-input v-model="form.siteName" :placeholder="t('settings.siteNameHint')" @input="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.embedEnabled')">
          <el-switch v-model="form.embedEnabled" @change="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.embedAllowedOrigins')">
          <el-input
            v-model="form.embedAllowedOrigins"
            type="textarea"
            :rows="3"
            :placeholder="t('settings.embedAllowedOriginsPlaceholder')"
            @input="markDirty"
          />
          <div class="hint">{{ t('settings.embedAllowedOriginsHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('settings.sqlTipsCollapsed')">
          <el-switch v-model="form.sqlTipsCollapsedDefault" @change="markDirty" />
          <div class="hint">{{ t('settings.sqlTipsCollapsedHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('settings.queryCache')">
          <el-switch v-model="form.queryCacheEnabled" @change="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.logsClearEnabled')">
          <el-switch v-model="form.logsClearEnabled" @change="markDirty" />
          <div class="hint">{{ t('settings.logsClearEnabledHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('settings.cacheTtl')">
          <el-input-number
            v-model="form.queryCacheTtlSeconds"
            :min="30"
            :max="86400"
            :disabled="!form.queryCacheEnabled"
            controls-position="right"
            @change="markDirty"
          />
          <div class="hint">{{ t('settings.cacheHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('settings.maxSessions')">
          <el-input-number
            v-model="form.maxConcurrentSessions"
            :min="0"
            :max="100"
            controls-position="right"
            @change="markDirty"
          />
          <div class="hint">{{ t('settings.maxSessionsHint') }}</div>
        </el-form-item>
      </el-card>

      <el-card class="mail-card">
        <template #header>
          <div class="section-title">{{ t('settings.mailSection') }}</div>
          <div class="hint">{{ t('settings.mailHint') }}</div>
        </template>
        <el-form-item :label="t('settings.mailHost')" prop="mailHost">
          <el-input v-model="form.mailHost" @input="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.mailPort')">
          <el-input-number
            v-model="form.mailPort"
            :min="1"
            :max="65535"
            controls-position="right"
            @change="markDirty"
          />
        </el-form-item>
        <el-form-item :label="t('settings.mailUsername')">
          <el-input v-model="form.mailUsername" autocomplete="off" @input="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.mailPassword')">
          <el-input
            v-model="form.mailPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="passwordSet ? t('settings.mailPasswordPlaceholder') : ''"
            @input="markDirty"
          />
        </el-form-item>
        <el-form-item :label="t('settings.mailFrom')" prop="mailFrom">
          <el-input v-model="form.mailFrom" @input="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.mailAuth')">
          <el-switch v-model="form.mailAuth" @change="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.mailStartTls')">
          <el-switch v-model="form.mailStartTls" @change="markDirty" />
        </el-form-item>
        <el-form-item :label="t('settings.mailTestTo')">
          <div class="test-row">
            <el-input v-model="form.mailTestTo" />
            <el-button :loading="testing" @click="testMail">{{ t('settings.mailTest') }}</el-button>
          </div>
        </el-form-item>
      </el-card>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.settings-form { max-width: 640px; display: flex; flex-direction: column; gap: 16px; }
.mail-card :deep(.el-card__header) { padding-bottom: 8px; }
.section-title { font-weight: 600; }
.hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.test-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.test-row .el-input { flex: 1; }
</style>
