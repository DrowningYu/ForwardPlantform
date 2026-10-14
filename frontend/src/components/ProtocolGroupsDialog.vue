<template>
  <el-dialog :model-value="modelValue" title="协议分组管理" width="640px"
    @update:model-value="emit('update:modelValue', $event)" @open="load">
    <el-form inline>
      <el-form-item label="名称"><el-input v-model="form.name" maxlength="128" placeholder="分组名称" /></el-form-item>
      <el-form-item label="排序码"><el-input-number v-model="form.sortOrder" :precision="0" :min="-2147483648" :max="2147483647" /></el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ form.id ? '保存修改' : '新建分组' }}</el-button>
        <el-button v-if="form.id" @click="reset">取消编辑</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="groups" border size="small">
      <el-table-column prop="name" label="分组名称" />
      <el-table-column prop="sortOrder" label="排序码" width="90" />
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" :disabled="saving" @click="Object.assign(form, row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="saving" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p class="hint">删除分组后，协议归入“未分组”，转发保持运行。排序码越小越靠前。</p>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type ProtocolGroup } from '../api'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'changed'): void }>()
const groups = ref<ProtocolGroup[]>([])
const saving = ref(false)
const form = reactive<{ id: number | null; name: string; sortOrder: number }>({ id: null, name: '', sortOrder: 0 })
const reset = () => Object.assign(form, { id: null, name: '', sortOrder: 0 })
async function load() { groups.value = await api.listGroups(); reset() }
async function save() {
  if (!form.name.trim()) { ElMessage.warning('请输入分组名称'); return }
  saving.value = true
  try {
    const body = { name: form.name.trim(), sortOrder: form.sortOrder ?? 0 }
    if (form.id) await api.updateGroup(form.id, body)
    else await api.createGroup(body)
    await load()
    emit('changed')
    ElMessage.success('分组已保存')
  } finally { saving.value = false }
}
async function remove(group: ProtocolGroup) {
  try { await ElMessageBox.confirm(`删除分组“${group.name}”？其中协议将归入未分组，转发不受影响。`, '删除分组', { type: 'warning' }) }
  catch { return }
  saving.value = true
  try {
    await api.deleteGroup(group.id)
    await load()
    emit('changed')
    ElMessage.success('分组已删除')
  } finally { saving.value = false }
}
</script>

<style scoped>.hint { color: var(--el-text-color-secondary); font-size: 13px; }</style>
