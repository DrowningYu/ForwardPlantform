<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>转发协议</span>
        <el-button type="primary" @click="openCreate">新建协议</el-button>
      </div>
    </template>

    <el-table :data="list" size="small" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.running ? 'success' : (row.status === 'ERROR' ? 'danger' : 'info')">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据源" width="120">
        <template #default="{ row }">{{ sourceName(row.sourceId) }}</template>
      </el-table-column>
      <el-table-column label="输出目标" width="120">
        <template #default="{ row }">{{ targetName(row.outputTargetId) }}</template>
      </el-table-column>
      <el-table-column prop="workerThreads" label="Worker" width="80" />
      <el-table-column prop="logRetentionDays" label="日志保留(天)" width="110" />
      <el-table-column label="操作" width="360">
        <template #default="{ row }">
          <el-button size="small" type="success" :disabled="row.running" @click="start(row)">启动</el-button>
          <el-button size="small" type="warning" :disabled="!row.running" @click="stop(row)">停止</el-button>
          <el-button size="small" @click="restart(row)">重启</el-button>
          <el-button size="small" type="primary" @click="edit(row)">配置</el-button>
          <el-button size="small" @click="goEditor(row)">代码/调试</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑协议' : '新建协议'" width="560px">
      <el-form label-width="130px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.sourceId" placeholder="选择数据源">
            <el-option v-for="s in sources" :key="s.id" :label="`${s.name} (${s.type})`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="输出目标">
          <el-select v-model="form.outputTargetId" placeholder="选择输出目标">
            <el-option v-for="t in targets" :key="t.id" :label="`${t.name} (${t.type})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="环形缓冲大小"><el-input-number v-model="form.ringBufferSize" :min="256" :step="1024" /></el-form-item>
        <el-form-item label="Worker 线程数"><el-input-number v-model="form.workerThreads" :min="1" :max="64" /></el-form-item>
        <el-form-item label="日志保留(天)"><el-input-number v-model="form.logRetentionDays" :min="1" :max="365" /></el-form-item>
        <el-form-item label="明细采样率">
          <el-slider v-model="form.sampleRate" :min="0" :max="1" :step="0.01" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, ConfigCarrier, Protocol } from '../api'

const router = useRouter()
const list = ref<Protocol[]>([])
const sources = ref<ConfigCarrier[]>([])
const targets = ref<ConfigCarrier[]>([])
const dialog = ref(false)
const form = reactive<any>({})

async function load() {
  list.value = await api.listProtocols()
  sources.value = await api.listDataSources()
  targets.value = await api.listOutputTargets()
}

const sourceName = (id?: number) => sources.value.find((s) => s.id === id)?.name || '-'
const targetName = (id?: number) => targets.value.find((t) => t.id === id)?.name || '-'

function openCreate() {
  Object.assign(form, {
    id: null, name: '', description: '', sourceId: undefined, outputTargetId: undefined,
    ringBufferSize: 16384, workerThreads: 4, logRetentionDays: 7, sampleRate: 1.0
  })
  dialog.value = true
}

function edit(row: Protocol) {
  Object.assign(form, { ...row })
  dialog.value = true
}

async function save() {
  if (form.id) await api.updateProtocol(form.id, form)
  else await api.createProtocol(form)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function start(row: Protocol) { await api.startProtocol(row.id); ElMessage.success('已启动'); load() }
async function stop(row: Protocol) { await api.stopProtocol(row.id); ElMessage.success('已停止'); load() }
async function restart(row: Protocol) { await api.restartProtocol(row.id); ElMessage.success('已重启'); load() }

async function remove(row: Protocol) {
  await ElMessageBox.confirm(`确认删除协议 ${row.name}? 将同时删除其脚本版本`, '提示', { type: 'warning' })
  await api.deleteProtocol(row.id)
  ElMessage.success('已删除')
  load()
}

function goEditor(row: Protocol) {
  router.push(`/protocols/${row.id}/editor`)
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
