<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { dataPolicyApi } from '@/api'

const field = reactive({ datasetId: '', userId: '', fieldName: '', allowed: true })
const row = reactive({ datasetId: '', userId: '', name: '', ruleJson: '' })
const deletion = reactive({ datasetId: '', ruleId: '' })

async function run(action: () => Promise<void>, success: string) {
  try { await action(); ElMessage.success(success) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}
</script>

<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">权限管理</h1></div>
    <el-tabs type="border-card">
      <el-tab-pane label="字段权限">
        <el-form label-width="100px">
          <el-form-item label="数据集 ID"><el-input v-model="field.datasetId" /></el-form-item><el-form-item label="用户 ID"><el-input v-model="field.userId" /></el-form-item><el-form-item label="字段名"><el-input v-model="field.fieldName" /></el-form-item><el-form-item label="允许"><el-switch v-model="field.allowed" /></el-form-item>
          <el-form-item><el-button type="primary" @click="run(() => dataPolicyApi.saveField(field.datasetId, { userId: field.userId, fieldName: field.fieldName, allowed: field.allowed }), '字段权限已保存')">保存</el-button><el-button type="danger" @click="run(() => dataPolicyApi.deleteField(field.datasetId, field.userId, field.fieldName), '字段权限已删除')">删除</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="行级规则">
        <el-form label-width="100px">
          <el-form-item label="数据集 ID"><el-input v-model="row.datasetId" /></el-form-item><el-form-item label="用户 ID"><el-input v-model="row.userId" placeholder="留空表示全体用户" /></el-form-item><el-form-item label="规则名称"><el-input v-model="row.name" /></el-form-item><el-form-item label="规则 JSON"><el-input v-model="row.ruleJson" type="textarea" :rows="5" placeholder='{"field":"region","operator":"EQ","value":"华东"}' /></el-form-item>
          <el-form-item><el-button type="primary" @click="run(() => dataPolicyApi.createRow(row.datasetId, { userId: row.userId || undefined, name: row.name, ruleJson: row.ruleJson }), '行级规则已创建')">创建规则</el-button></el-form-item>
          <el-divider />
          <el-form-item label="数据集 ID"><el-input v-model="deletion.datasetId" /></el-form-item><el-form-item label="规则 ID"><el-input v-model="deletion.ruleId" /></el-form-item><el-form-item><el-button type="danger" @click="run(() => dataPolicyApi.deleteRow(deletion.datasetId, deletion.ruleId), '行级规则已删除')">删除规则</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

