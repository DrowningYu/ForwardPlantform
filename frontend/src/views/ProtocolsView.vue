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

      <el-table-column prop="name" label="名称" width="180" show-overflow-tooltip />

      <el-table-column label="状态" width="100">

        <template #default="{ row }">

          <el-tag :type="row.running ? 'success' : (row.status === 'ERROR' ? 'danger' : 'info')">{{ row.status }}</el-tag>

        </template>

      </el-table-column>

      <el-table-column label="数据源" min-width="160" show-overflow-tooltip>

        <template #default="{ row }">{{ sourceName(row.sourceId) }}</template>

      </el-table-column>

      <el-table-column label="输出目标" min-width="160" show-overflow-tooltip>

        <template #default="{ row }">{{ targetName(row.outputTargetId) }}</template>

      </el-table-column>

      <el-table-column prop="workerThreads" label="Worker" width="80" />

      <el-table-column prop="logRetentionDays" label="日志保留(天)" width="110" />

      <el-table-column label="操作" width="350" fixed="right">

        <template #default="{ row }">

          <div class="action-btns">

            <el-button size="small" type="success" :disabled="row.running" @click="start(row)">启动</el-button>

            <el-button size="small" type="warning" :disabled="!row.running" @click="stop(row)">停止</el-button>

            <el-button size="small" @click="restart(row)">重启</el-button>

            <el-button size="small" type="primary" @click="edit(row)">配置</el-button>

            <el-button size="small" @click="goEditor(row)">代码/调试</el-button>

            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>

          </div>

        </template>

      </el-table-column>

    </el-table>



    <el-dialog v-model="dialog" :title="form.id ? '编辑协议' : '新建协议'" width="560px" @opened="onDialogOpened">

      <el-form label-width="130px">

        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>

        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>

        <el-form-item label="数据源">

          <el-select v-model="form.sourceId" placeholder="选择数据源" class="full-width">

            <el-option v-for="s in sources" :key="s.id" :label="optionSourceLabel(s)" :value="s.id">

              <span class="option-row">

                <span>{{ s.name }} ({{ s.type }})</span>

                <BindingWarningIcon :result="sourceWarnings.get(s.id)" />

              </span>

            </el-option>

          </el-select>

          <el-alert

            v-for="(msg, idx) in selectedSourceAlerts"

            :key="'src-' + idx"

            :title="msg"

            :type="selectedSourceLevel === 'BLOCK' ? 'error' : 'warning'"

            show-icon

            :closable="false"

            class="binding-alert"

          />

        </el-form-item>

        <el-form-item label="输出目标">

          <el-select v-model="form.outputTargetId" placeholder="选择输出目标" class="full-width">

            <el-option v-for="t in targets" :key="t.id" :label="optionTargetLabel(t)" :value="t.id">

              <span class="option-row">

                <span>{{ t.name }} ({{ t.type }})</span>

                <BindingWarningIcon :result="targetWarnings.get(t.id)" />

              </span>

            </el-option>

          </el-select>

          <el-alert

            v-for="(msg, idx) in selectedTargetAlerts"

            :key="'tgt-' + idx"

            :title="msg"

            :type="selectedTargetLevel === 'BLOCK' ? 'error' : 'warning'"

            show-icon

            :closable="false"

            class="binding-alert"

          />

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

import { computed, onMounted, reactive, ref } from 'vue'

import { useRouter } from 'vue-router'

import { ElMessage, ElMessageBox } from 'element-plus'

import { api, BindingCheckResult, ConfigCarrier, Protocol } from '../api'

import BindingWarningIcon from '../components/BindingWarningIcon.vue'

import { formatBindingMessages } from '../utils/bindingWarnings'



const router = useRouter()

const list = ref<Protocol[]>([])

const sources = ref<ConfigCarrier[]>([])

const targets = ref<ConfigCarrier[]>([])

const dialog = ref(false)

const form = reactive<any>({})

const sourceWarnings = ref(new Map<number, BindingCheckResult>())

const targetWarnings = ref(new Map<number, BindingCheckResult>())



async function load() {

  list.value = await api.listProtocols()

  sources.value = await api.listDataSources()

  targets.value = await api.listOutputTargets()

}



const sourceName = (id?: number) => sources.value.find((s) => s.id === id)?.name || '-'

const targetName = (id?: number) => targets.value.find((t) => t.id === id)?.name || '-'



function optionSourceLabel(s: ConfigCarrier) {

  return `${s.name} (${s.type})`

}



function optionTargetLabel(t: ConfigCarrier) {

  return `${t.name} (${t.type})`

}



const selectedSourceResult = computed(() =>

  form.sourceId ? sourceWarnings.value.get(form.sourceId) : undefined

)

const selectedTargetResult = computed(() =>

  form.outputTargetId ? targetWarnings.value.get(form.outputTargetId) : undefined

)



const selectedSourceLevel = computed(() => {

  const r = selectedSourceResult.value

  if (!r) return null

  if (r.blockers?.length) return 'BLOCK'

  if (r.warnings?.length) return 'WARN'

  return null

})



const selectedTargetLevel = computed(() => {

  const r = selectedTargetResult.value

  if (!r) return null

  if (r.blockers?.length) return 'BLOCK'

  if (r.warnings?.length) return 'WARN'

  return null

})



const selectedSourceAlerts = computed(() => {

  const r = selectedSourceResult.value

  if (!r) return []

  return [...(r.blockers || []), ...(r.warnings || [])].map((w) => w.message)

})



const selectedTargetAlerts = computed(() => {

  const r = selectedTargetResult.value

  if (!r) return []

  return [...(r.blockers || []), ...(r.warnings || [])].map((w) => w.message)

})



async function loadOptionWarnings() {

  const protocolId = form.id ?? undefined

  const srcMap = new Map<number, BindingCheckResult>()

  const tgtMap = new Map<number, BindingCheckResult>()

  await Promise.all([

    ...sources.value.map(async (s) => {

      srcMap.set(

        s.id,

        await api.getBindingWarnings({ protocolId, sourceId: s.id })

      )

    }),

    ...targets.value.map(async (t) => {

      tgtMap.set(

        t.id,

        await api.getBindingWarnings({ protocolId, outputTargetId: t.id })

      )

    })

  ])

  sourceWarnings.value = srcMap

  targetWarnings.value = tgtMap

}



async function onDialogOpened() {

  await loadOptionWarnings()

}



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



function buildPayload() {

  return {

    name: form.name,

    description: form.description,

    sourceId: form.sourceId,

    outputTargetId: form.outputTargetId,

    ringBufferSize: form.ringBufferSize,

    workerThreads: form.workerThreads,

    logRetentionDays: form.logRetentionDays,

    sampleRate: form.sampleRate

  }

}



async function save() {

  const body = buildPayload()

  if (form.id) await api.updateProtocol(form.id, body)

  else await api.createProtocol(body)

  ElMessage.success('已保存')

  dialog.value = false

  load()

}



async function runWithBindingCheck(
  row: Protocol,
  action: 'start' | 'restart',
  successText: string,
  confirmTitle: string
) {
  const check = await api.checkProtocolStart(row.id)
  if (check.blockers.length > 0) {
    await ElMessageBox.alert(formatBindingMessages(check), '无法启动', {
      type: 'error',
      confirmButtonText: '确认并返回',
      showCancelButton: false
    })
    return
  }
  const needsAck = check.warnings.length > 0
  if (needsAck) {
    try {
      await ElMessageBox.confirm(formatBindingMessages(check), confirmTitle, {
        type: 'warning',
        confirmButtonText: '我已了解，依然启动',
        cancelButtonText: '返回'
      })
    } catch {
      return
    }
  }
  const opts = needsAck ? { acknowledgeWarnings: true } : undefined
  if (action === 'start') {
    await api.startProtocol(row.id, opts)
  } else {
    await api.restartProtocol(row.id, opts)
  }
  ElMessage.success(successText)
  load()
}



async function start(row: Protocol) {

  try {

    await runWithBindingCheck(row, 'start', '已启动', '启动确认')

  } catch (e: any) {

    const code = e?.response?.data?.code

    if (code === 'BINDING_BLOCKED') {

      await ElMessageBox.alert(formatBindingMessages(e.response.data), '无法启动', {

        type: 'error',

        confirmButtonText: '确认并返回',

        showCancelButton: false

      })

    } else if (code === 'BINDING_WARNING') {

      // 预检已处理，兜底不再重复

    }

  }

}



async function stop(row: Protocol) {

  await api.stopProtocol(row.id)

  ElMessage.success('已停止')

  load()

}



async function restart(row: Protocol) {

  try {

    await runWithBindingCheck(row, 'restart', '已重启', '重启确认')

  } catch (e: any) {

    const code = e?.response?.data?.code

    if (code === 'BINDING_BLOCKED') {

      await ElMessageBox.alert(formatBindingMessages(e.response.data), '无法启动', {

        type: 'error',

        confirmButtonText: '确认并返回',

        showCancelButton: false

      })

    }

  }

}



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

.action-btns {

  display: flex;

  flex-wrap: nowrap;

  align-items: center;

  gap: 4px;

}

.action-btns :deep(.el-button + .el-button) {

  margin-left: 0;

}

.full-width { width: 100%; }

.option-row {

  display: flex;

  align-items: center;

  justify-content: space-between;

  width: 100%;

}

.binding-alert {

  margin-top: 8px;

}

.binding-alert :deep(.el-alert__title) {

  white-space: pre-wrap;

  line-height: 1.5;

  font-size: 12px;

}

</style>

