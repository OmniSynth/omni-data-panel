<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { confirmBox } from '@/i18n/dialog'
import { dataPolicyApi, userApi } from '@/api'
import FilterConditionEditor from '@/components/FilterConditionEditor.vue'
import { conditionsToRuleJson, ruleJsonToConditions } from '@/utils/filterRule'
import type { Dataset, FilterCondition, Id, RowRule, UserDirectoryItem } from '@/types'

const props = defineProps<{
  dataset?: Dataset
}>()
const visible = defineModel<boolean>({ required: true })
const { t } = useI18n()

const users = ref<UserDirectoryItem[]>([])
const selectedUserId = ref<Id | ''>('')
const allowedFields = ref<string[]>([])
const fieldRestricted = ref(false)
const rows = ref<RowRule[]>([])
const loading = ref(false)
const savingFields = ref(false)
const ruleDialogVisible = ref(false)
const editingRuleId = ref<Id>()
const ruleForm = reactive({
  name: '',
  scope: 'user' as 'user' | 'all',
  userId: '' as Id | '',
  enabled: true,
  conditions: [] as FilterCondition[],
})
const savingRule = ref(false)

const fieldOptions = computed(() => props.dataset?.fields || [])
const datasetId = computed(() => props.dataset?.id)
const userLabel = (user: UserDirectoryItem) =>
  user.displayName ? `${user.displayName} (${user.username})` : user.username

function userName(userId?: Id | null) {
  if (userId === undefined || userId === null || userId === '') return t('datasetPolicy.allUsers')
  const hit = users.value.find((item) => String(item.id) === String(userId))
  return hit ? userLabel(hit) : String(userId)
}

async function loadUsers() {
  users.value = await userApi.directory()
}

async function loadFields() {
  if (!datasetId.value || selectedUserId.value === '') return
  const list = await dataPolicyApi.listFields(datasetId.value, selectedUserId.value)
  fieldRestricted.value = list.length > 0
  allowedFields.value = list.filter((item) => item.allowed).map((item) => item.fieldName)
}

async function loadRows() {
  if (!datasetId.value) return
  rows.value = await dataPolicyApi.listRows(datasetId.value)
}

async function loadAll() {
  if (!datasetId.value) return
  loading.value = true
  try {
    await loadUsers()
    if (!selectedUserId.value && users.value.length) {
      selectedUserId.value = users.value[0].id
    }
    await Promise.all([loadFields(), loadRows()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function saveFields() {
  if (!datasetId.value || selectedUserId.value === '') return
  savingFields.value = true
  try {
    await dataPolicyApi.replaceFields(datasetId.value, {
      userId: selectedUserId.value,
      allowedFields: allowedFields.value,
    })
    fieldRestricted.value = allowedFields.value.length > 0
    ElMessage.success(t('datasetPolicy.fieldsSaved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.saveFailed'))
  } finally {
    savingFields.value = false
  }
}

async function clearFields() {
  if (!datasetId.value || selectedUserId.value === '') return
  try {
    await confirmBox(t('datasetPolicy.clearFieldsConfirm'), t('common.deleteConfirmTitle'))
  } catch {
    return
  }
  savingFields.value = true
  try {
    await dataPolicyApi.replaceFields(datasetId.value, {
      userId: selectedUserId.value,
      allowedFields: [],
    })
    allowedFields.value = []
    fieldRestricted.value = false
    ElMessage.success(t('datasetPolicy.fieldsCleared'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.saveFailed'))
  } finally {
    savingFields.value = false
  }
}

function openCreateRule() {
  editingRuleId.value = undefined
  ruleForm.name = ''
  ruleForm.scope = selectedUserId.value === '' ? 'all' : 'user'
  ruleForm.userId = selectedUserId.value
  ruleForm.enabled = true
  ruleForm.conditions = [{ field: '', operator: 'EQ', value: '' }]
  ruleDialogVisible.value = true
}

function openEditRule(rule: RowRule) {
  editingRuleId.value = rule.id
  ruleForm.name = rule.name
  ruleForm.scope = rule.userId == null || rule.userId === '' ? 'all' : 'user'
  ruleForm.userId = rule.userId ?? ''
  ruleForm.enabled = rule.enabled !== false
  const parsed = ruleJsonToConditions(rule.ruleJson)
  ruleForm.conditions = parsed.length ? parsed : [{ field: '', operator: 'EQ', value: '' }]
  ruleDialogVisible.value = true
}

async function saveRule() {
  if (!datasetId.value) return
  if (!ruleForm.name.trim()) return ElMessage.warning(t('datasetPolicy.needRuleName'))
  let ruleJson: string
  try {
    ruleJson = conditionsToRuleJson(ruleForm.conditions)
  } catch {
    return ElMessage.warning(t('datasetPolicy.needCondition'))
  }
  const userId = ruleForm.scope === 'all' ? undefined : ruleForm.userId || undefined
  if (ruleForm.scope === 'user' && (userId === undefined || userId === '')) {
    return ElMessage.warning(t('datasetPolicy.needUser'))
  }
  savingRule.value = true
  try {
    const payload = { userId, name: ruleForm.name.trim(), ruleJson, enabled: ruleForm.enabled }
    if (editingRuleId.value !== undefined) {
      await dataPolicyApi.updateRow(datasetId.value, editingRuleId.value, payload)
    } else {
      await dataPolicyApi.createRow(datasetId.value, payload)
    }
    ruleDialogVisible.value = false
    ElMessage.success(t('datasetPolicy.ruleSaved'))
    await loadRows()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.saveFailed'))
  } finally {
    savingRule.value = false
  }
}

async function removeRule(rule: RowRule) {
  if (!datasetId.value) return
  try {
    await confirmBox(t('datasetPolicy.deleteRuleConfirm'), t('common.deleteConfirmTitle'))
    await dataPolicyApi.deleteRow(datasetId.value, rule.id)
    ElMessage.success(t('datasetPolicy.ruleDeleted'))
    await loadRows()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.saveFailed'))
    }
  }
}

watch(visible, (opened) => {
  if (opened) loadAll()
  else {
    users.value = []
    rows.value = []
    allowedFields.value = []
    selectedUserId.value = ''
    fieldRestricted.value = false
  }
})

watch(selectedUserId, () => {
  if (visible.value) loadFields().catch((error) => {
    ElMessage.error(error instanceof Error ? error.message : t('datasetPolicy.loadFailed'))
  })
})
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="t('datasetPolicy.title', { name: dataset?.name || '' })"
    size="640px"
    destroy-on-close
  >
    <div v-loading="loading" class="policy-body">
      <section class="block">
        <div class="block-head">
          <h3>{{ t('datasetPolicy.fieldTitle') }}</h3>
          <p class="hint">{{ t('datasetPolicy.fieldHint') }}</p>
        </div>
        <el-form label-width="88px">
          <el-form-item :label="t('datasetPolicy.user')">
            <el-select v-model="selectedUserId" filterable class="full-width" :placeholder="t('datasetPolicy.selectUser')">
              <el-option
                v-for="user in users"
                :key="user.id"
                :label="userLabel(user)"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('datasetPolicy.visibleFields')">
            <el-checkbox-group v-model="allowedFields" class="field-group">
              <el-checkbox v-for="field in fieldOptions" :key="field.name" :value="field.name">
                {{ field.name }}
              </el-checkbox>
            </el-checkbox-group>
            <p v-if="!fieldOptions.length" class="hint">{{ t('datasetPolicy.noFields') }}</p>
            <p class="status">
              {{ fieldRestricted ? t('datasetPolicy.restricted') : t('datasetPolicy.unrestricted') }}
            </p>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingFields" :disabled="!selectedUserId" @click="saveFields">
              {{ t('datasetPolicy.saveFields') }}
            </el-button>
            <el-button :disabled="!selectedUserId || !fieldRestricted" :loading="savingFields" @click="clearFields">
              {{ t('datasetPolicy.clearFields') }}
            </el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="block">
        <div class="block-head row">
          <div>
            <h3>{{ t('datasetPolicy.rowTitle') }}</h3>
            <p class="hint">{{ t('datasetPolicy.rowHint') }}</p>
          </div>
          <el-button type="primary" @click="openCreateRule">{{ t('datasetPolicy.addRule') }}</el-button>
        </div>
        <el-table :data="rows" :empty-text="t('datasetPolicy.noRules')">
          <el-table-column prop="name" :label="t('datasetPolicy.ruleName')" min-width="120" />
          <el-table-column :label="t('datasetPolicy.scope')" min-width="140">
            <template #default="{ row }">{{ userName(row.userId) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled === false ? 'info' : 'success'" effect="plain">
                {{ row.enabled === false ? t('common.disabled') : t('common.enabled') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditRule(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="danger" @click="removeRule(row)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog
      v-model="ruleDialogVisible"
      :title="editingRuleId === undefined ? t('datasetPolicy.createRule') : t('datasetPolicy.editRule')"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-form-item :label="t('datasetPolicy.ruleName')">
          <el-input v-model="ruleForm.name" />
        </el-form-item>
        <el-form-item :label="t('datasetPolicy.scope')">
          <el-radio-group v-model="ruleForm.scope">
            <el-radio-button value="user">{{ t('datasetPolicy.specificUser') }}</el-radio-button>
            <el-radio-button value="all">{{ t('datasetPolicy.allUsers') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="ruleForm.scope === 'user'" :label="t('datasetPolicy.user')">
          <el-select v-model="ruleForm.userId" filterable class="full-width">
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.enabled')">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item :label="t('datasetPolicy.conditions')">
          <FilterConditionEditor v-model="ruleForm.conditions" :fields="fieldOptions" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingRule" @click="saveRule">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.policy-body { display: flex; flex-direction: column; gap: 24px; }
.block-head h3 { margin: 0 0 4px; font-size: 15px; }
.block-head.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.hint { margin: 0; color: var(--omni-muted); font-size: 12px; line-height: 1.5; }
.status { margin: 8px 0 0; font-size: 12px; color: var(--omni-muted); }
.field-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.full-width { width: 100%; }
</style>
