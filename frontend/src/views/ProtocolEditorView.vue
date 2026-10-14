<template>
  <div class="editor-page">
    <div class="toolbar">
      <span class="title">协议 #{{ protocolId }} · {{ protocol?.name }}</span>
      <el-tag v-if="protocol" :type="protocol.running ? 'success' : 'info'">{{ protocol.status }}</el-tag>
      <div class="spacer" />
      <el-tag v-if="loadedVersion" type="info">正在编辑 v{{ loadedVersion.version }} · 修订 {{ loadedVersion.revision }}</el-tag>
      <el-tag v-if="dirty" type="warning">未保存</el-tag>
      <el-input v-model="remark" :disabled="saving" maxlength="512" placeholder="版本备注(可选)" style="width: 220px" size="small" />
      <el-button size="small" :disabled="!loadedVersion || !dirty || saving" @click="revise">保存修订</el-button>
      <el-button type="primary" size="small" :loading="saving" @click="save">保存为新版本并启用</el-button>
      <el-button size="small" @click="$router.push('/protocols')">返回</el-button>
    </div>

    <el-row :gutter="12" class="body">
      <el-col :span="14" class="col">
        <div class="pane-title">Groovy 处理脚本</div>
        <div class="editor-wrap">
          <vue-monaco-editor
            v-model:value="code"
            language="java"
            theme="vs-dark"
            :options="{ fontSize: 13, minimap: { enabled: false }, automaticLayout: true, scrollBeyondLastLine: false, readOnly: saving }"
          />
        </div>
      </el-col>

      <el-col :span="10" class="col">
        <div class="pane-title">调试</div>
        <div class="debug-pane">
          <div class="row">
            <el-checkbox v-model="multiMode">每行一条样本</el-checkbox>
            <div class="spacer" />
            <el-button size="small" :loading="capturing" @click="capture">抓取实时样本</el-button>
            <el-button type="primary" size="small" :loading="running" @click="run">运行调试</el-button>
          </div>
          <el-input
            v-model="sampleInput"
            type="textarea"
            :rows="6"
            placeholder="在此粘贴模拟输入（原始报文）。可点击“抓取实时样本”从数据源获取。"
          />

          <div class="result-area">
            <el-alert v-if="compileError" :title="'编译错误: ' + compileError" type="error" :closable="false" show-icon />
            <div v-for="(c, idx) in cases" :key="idx" class="case">
              <div class="case-head">
                <span>样本 #{{ idx + 1 }}</span>
                <el-tag size="small" :type="c.success ? 'success' : 'danger'">
                  {{ c.timeout ? '超时' : (c.success ? '成功' : '失败') }} · {{ c.costMs }}ms
                </el-tag>
              </div>
              <div v-if="c.error" class="err">错误: {{ c.error }}</div>
              <div class="sub">输出 ({{ c.outputs.length }}):</div>
              <pre v-for="(o, i) in c.outputs" :key="i" class="output">{{ o }}</pre>
              <template v-if="c.logs.length">
                <div class="sub">日志:</div>
                <pre v-for="(l, i) in c.logs" :key="'l' + i" class="log">{{ l }}</pre>
              </template>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-collapse v-model="expandedSections" class="bottom">
      <el-collapse-item title="平台提供的变量与函数（点击展开）" name="help">
        <pre class="help">msg / payload   收到的原始报文(字符串)
ctx             元数据: ctx.topic / ctx.source / ctx.receivedAt / ctx.partition / ctx.offset
json            json.parse(str) 解析JSON;  json.stringify(obj) 序列化
state           跨消息聚合(带TTL): state.put(k,v) / state.put(k,v,ttlMs) / state.get(k) / state.remove(k)
time            time.toEpochMillis(x) 统一为毫秒;  time.nowMs();  time.format(ms,'yyyy-MM-dd HH:mm:ss')
log             log.info/warn/error/debug(msg)
output(data)          将 data 发送到协议配置的输出目标(平台自动序列化)
output('key', data)   发送到指定目标(多目标场景)</pre>
      </el-collapse-item>
      <el-collapse-item :title="`历史版本 (${versions.length})`" name="versions">
        <el-table :data="versions" size="small" border :row-class-name="versionRowClass">
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="使用状态" width="240">
            <template #default="{ row }">
              <el-tag v-if="row.current" size="small">当前配置</el-tag>
              <el-tag v-if="row.running" size="small" type="success">正在运行</el-tag>
              <el-tag v-if="loadedVersion?.id === row.id" size="small" type="info">编辑中</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="revision" label="修订" width="65" />
          <el-table-column prop="compileStatus" label="编译" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.compileStatus === 'OK' ? 'success' : 'danger'">{{ row.compileStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" />
          <el-table-column prop="createdAt" label="创建时间" width="200" />
          <el-table-column prop="updatedAt" label="修订时间" width="200" />
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :disabled="saving" @click="loadVersion(row.id)">载入/修订</el-button>
              <el-button size="small" type="primary" :disabled="row.current || row.compileStatus !== 'OK' || saving" @click="activate(row.id)">设为当前</el-button>
              <el-tooltip :disabled="!row.current && !row.running" content="请先切换到其他有效版本再删除">
                <span><el-button size="small" type="danger" :disabled="row.current || row.running || saving" @click="removeVersion(row)">删除</el-button></span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { VueMonacoEditor } from '@guolao/vue-monaco-editor'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, Protocol, type ScriptVersion } from '../api'

const route = useRoute()
const protocolId = Number(route.params.id)

const protocol = ref<Protocol | null>(null)
const code = ref('')
const remark = ref('')
const versions = ref<ScriptVersion[]>([])
const loadedVersion = ref<ScriptVersion | null>(null)
const expandedSections = ref(['versions'])
const savedCode = ref('')
const savedRemark = ref('')
const dirty = computed(() => code.value !== savedCode.value || remark.value !== savedRemark.value)
const versionRowClass = ({ row }: { row: ScriptVersion }) => row.current ? 'current-version' : ''

const sampleInput = ref('')
const multiMode = ref(false)
const cases = ref<any[]>([])
const compileError = ref('')

const saving = ref(false)
const running = ref(false)
const capturing = ref(false)

async function loadAll() {
  await refreshVersions()
  const cur = await api.currentScript(protocolId)
  if (cur) selectVersion(cur)
}

async function refreshVersions() {
  const [p, v] = await Promise.all([api.getProtocol(protocolId), api.listVersions(protocolId)])
  protocol.value = p
  versions.value = v
}

function selectVersion(v: ScriptVersion) {
  loadedVersion.value = v
  code.value = savedCode.value = v.code || ''
  remark.value = savedRemark.value = v.remark || ''
}

async function confirm(message: string) {
  try { await ElMessageBox.confirm(message, '确认操作', { type: 'warning' }); return true }
  catch { return false }
}

async function discardChanges() {
  return !dirty.value || await confirm('当前代码或备注尚未保存，是否放弃修改？')
}

async function confirmReload() {
  return !protocol.value?.running || await confirm('此操作将重载正在运行的协议，短暂中断该协议转发并清空其内存 state。是否继续？')
}

async function save() {
  if (!await confirmReload()) return
  saving.value = true
  try {
    const v = await api.saveScript(protocolId, { code: code.value, remark: remark.value })
    selectVersion(v)
    compileError.value = v.compileError || ''
    if (v.compileStatus === 'OK') ElMessage.success('已保存并设为当前版本')
    else ElMessage.warning('已保存失败版本，编译未通过，当前使用版本保持不变')
  } finally {
    saving.value = false
    await refreshVersions()
  }
}

async function revise() {
  const v = loadedVersion.value
  if (!v) return
  if (protocol.value?.currentVersionId === v.id && code.value !== savedCode.value && !await confirmReload()) return
  saving.value = true
  try {
    const updated = await api.reviseScript(protocolId, v.id, { code: code.value, remark: remark.value, revision: v.revision })
    selectVersion(updated)
    compileError.value = updated.compileError || ''
    ElMessage.success('版本修订已保存')
  } finally { saving.value = false; await refreshVersions() }
}

function buildInputs(): string[] {
  if (multiMode.value) {
    return sampleInput.value.split('\n').map((s) => s.trim()).filter((s) => s.length > 0)
  }
  return sampleInput.value.trim() ? [sampleInput.value] : []
}

async function run() {
  const inputs = buildInputs()
  if (inputs.length === 0) {
    ElMessage.warning('请先填写模拟输入')
    return
  }
  running.value = true
  compileError.value = ''
  cases.value = []
  try {
    const res = await api.debugRun({ code: code.value, inputs })
    if (!res.compileOk) {
      compileError.value = res.compileError
      return
    }
    cases.value = res.cases
  } finally {
    running.value = false
  }
}

async function capture() {
  if (!protocol.value?.sourceId) {
    ElMessage.warning('该协议未配置数据源')
    return
  }
  capturing.value = true
  try {
    const res = await api.debugCapture({ sourceId: protocol.value.sourceId, max: 10, timeoutMs: 10000 })
    if (res.count > 0) {
      multiMode.value = true
      sampleInput.value = res.samples.join('\n')
      ElMessage.success(`抓取到 ${res.count} 条样本`)
    } else {
      ElMessage.info('超时内未抓取到样本')
    }
  } finally {
    capturing.value = false
  }
}

async function loadVersion(vid: number) {
  if (!await discardChanges()) return
  saving.value = true
  try {
    const v = await api.getVersion(protocolId, vid)
    selectVersion(v)
    compileError.value = v.compileError || ''
    ElMessage.success('已载入该版本代码到编辑器')
  } finally { saving.value = false }
}

async function activate(vid: number) {
  if (!await discardChanges() || !await confirmReload()) return
  saving.value = true
  try {
    selectVersion(await api.activateVersion(protocolId, vid))
    ElMessage.success('已设为当前版本')
  } finally { saving.value = false; await refreshVersions() }
}

async function removeVersion(v: ScriptVersion) {
  if (loadedVersion.value?.id === v.id && !await discardChanges()) return
  if (!await confirm(`确认永久删除历史版本 v${v.version}？`)) return
  saving.value = true
  try {
    await api.deleteScript(protocolId, v.id)
    if (loadedVersion.value?.id === v.id) {
      const current = await api.currentScript(protocolId)
      if (current) selectVersion(current)
      else { loadedVersion.value = null; code.value = savedCode.value = ''; remark.value = savedRemark.value = '' }
    }
    ElMessage.success('历史版本已删除')
  } finally { saving.value = false; await refreshVersions() }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) { event.preventDefault(); event.returnValue = '' }
}
onBeforeRouteLeave(() => saving.value ? false : discardChanges())
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))

onMounted(loadAll)
</script>

<style scoped>
.editor-page { display: flex; flex-direction: column; height: calc(100vh - 120px); }
.toolbar { display: flex; align-items: center; gap: 10px; background: #fff; padding: 10px 12px; border-radius: 4px; }
.toolbar { flex-wrap: wrap; }
:deep(.current-version) { --el-table-tr-bg-color: #ecf5ff; }
.toolbar .title { font-weight: 600; }
.spacer { flex: 1; }
.body { flex: 1; margin-top: 12px; min-height: 0; }
.col { height: 100%; }
.pane-title { font-weight: 600; margin-bottom: 6px; }
.editor-wrap { height: calc(100% - 24px); border: 1px solid #ddd; }
.debug-pane { height: calc(100% - 24px); background: #fff; border: 1px solid #ddd; padding: 10px; display: flex; flex-direction: column; }
.debug-pane .row { display: flex; align-items: center; margin-bottom: 8px; }
.result-area { flex: 1; overflow: auto; margin-top: 8px; }
.case { border: 1px solid #eee; border-radius: 4px; padding: 8px; margin-bottom: 8px; }
.case-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.sub { color: #909399; font-size: 12px; margin: 4px 0; }
.output { background: #f6f8fa; padding: 6px; margin: 2px 0; white-space: pre-wrap; word-break: break-all; }
.log { background: #fff7e6; padding: 4px 6px; margin: 2px 0; white-space: pre-wrap; }
.err { color: #f56c6c; margin: 4px 0; }
.bottom { margin-top: 12px; background: #fff; padding: 0 12px; border-radius: 4px; }
.help { white-space: pre-wrap; line-height: 1.7; color: #444; }
</style>
