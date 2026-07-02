<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>输出目标（转发端）</span>
        <el-button type="primary" @click="openCreate">新建输出目标</el-button>
      </div>
    </template>

    <el-table :data="list" size="small" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }"><el-tag type="success">{{ row.type }}</el-tag></template>
      </el-table-column>
      <el-table-column label="连接" show-overflow-tooltip>
        <template #default="{ row }">{{ summarize(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑输出目标' : '新建输出目标'" width="560px">
      <el-form label-width="120px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" @change="onTypeChange">
            <el-option label="MQTT" value="MQTT" />
            <el-option label="KAFKA" value="KAFKA" />
            <el-option label="HTTP" value="HTTP" />
          </el-select>
        </el-form-item>

        <template v-if="form.type === 'MQTT'">
          <el-form-item label="Broker URL"><el-input v-model="cfg.url" placeholder="tcp://host:1883" /></el-form-item>
          <el-form-item label="Client ID"><el-input v-model="cfg.clientId" /></el-form-item>
          <el-form-item label="发布主题"><el-input v-model="cfg.topic" /></el-form-item>
          <el-form-item label="QoS"><el-input-number v-model="cfg.qos" :min="0" :max="2" /></el-form-item>
          <el-form-item label="Retained"><el-switch v-model="cfg.retained" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="cfg.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="cfg.password" type="password" show-password /></el-form-item>
        </template>

        <template v-else-if="form.type === 'KAFKA'">
          <el-form-item label="Bootstrap"><el-input v-model="cfg.bootstrapServers" /></el-form-item>
          <el-form-item label="发布主题"><el-input v-model="cfg.topic" /></el-form-item>
          <el-form-item label="SASL机制"><el-input v-model="cfg.saslMechanism" placeholder="可空" /></el-form-item>
          <el-form-item label="安全协议"><el-input v-model="cfg.securityProtocol" placeholder="可空" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="cfg.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="cfg.password" type="password" show-password /></el-form-item>
        </template>

        <template v-else-if="form.type === 'HTTP'">
          <el-form-item label="URL"><el-input v-model="cfg.url" placeholder="https://..." /></el-form-item>
          <el-form-item label="方法">
            <el-select v-model="cfg.method"><el-option label="POST" value="POST" /><el-option label="PUT" value="PUT" /></el-select>
          </el-form-item>
          <el-form-item label="Content-Type"><el-input v-model="cfg.contentType" /></el-form-item>
          <el-form-item label="超时(ms)"><el-input-number v-model="cfg.timeoutMs" :min="500" :step="500" /></el-form-item>
        </template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, ConfigCarrier } from '../api'

const list = ref<ConfigCarrier[]>([])
const dialog = ref(false)
const form = reactive<any>({ id: null, name: '', type: 'MQTT' })
const cfg = reactive<any>({})

async function load() { list.value = await api.listOutputTargets() }

function summarize(row: ConfigCarrier) {
  const c = row.config || {}
  if (row.type === 'MQTT') return `${c.url} topic=${c.topic}`
  if (row.type === 'KAFKA') return `${c.bootstrapServers} topic=${c.topic}`
  if (row.type === 'HTTP') return `${c.method} ${c.url}`
  return JSON.stringify(c)
}

function resetCfg(preset: any = {}) {
  Object.keys(cfg).forEach((k) => delete cfg[k])
  Object.assign(cfg, preset)
}

function onTypeChange() {
  if (form.type === 'MQTT') resetCfg({ qos: 1, retained: false })
  else if (form.type === 'KAFKA') resetCfg({})
  else resetCfg({ method: 'POST', contentType: 'application/json', timeoutMs: 5000 })
}

function openCreate() {
  form.id = null; form.name = ''; form.type = 'MQTT'
  onTypeChange()
  dialog.value = true
}

function openEdit(row: ConfigCarrier) {
  form.id = row.id; form.name = row.name; form.type = row.type
  resetCfg({ ...(row.config || {}) })
  dialog.value = true
}

async function save() {
  const body = { name: form.name, type: form.type, config: { ...cfg } }
  if (form.id) await api.updateOutputTarget(form.id, body)
  else await api.createOutputTarget(body)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function remove(row: ConfigCarrier) {
  await ElMessageBox.confirm(`确认删除输出目标 ${row.name}?`, '提示', { type: 'warning' })
  await api.deleteOutputTarget(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
