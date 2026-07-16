<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num">{{ overview.runningCount ?? 0 }}</div><div class="label">运行中协议</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num">{{ overview.totalIn ?? 0 }}</div><div class="label">累计接收</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num">{{ overview.totalOut ?? 0 }}</div><div class="label">累计输出</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num err">{{ overview.totalScriptError ?? 0 }}</div><div class="label">脚本错误</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num err">{{ overview.totalSinkError ?? 0 }}</div><div class="label">输出错误</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="stat"><div class="num">{{ overview.recordQueueSize ?? 0 }}</div><div class="label">日志队列</div></div></el-card></el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>CPU 占用</span></template>
          <ResourceDonutChart
            title="CPU"
            :center-text="cpuCenterText"
            :segments="cpuSegments"
            :unavailable="!sys.available"
          />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>内存占用</span></template>
          <ResourceDonutChart
            title="内存"
            :center-text="memCenterText"
            :segments="memSegments"
            :unavailable="!sys.available"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>协议运行状态</span>
          <el-switch v-model="auto" active-text="自动刷新(5s)" />
        </div>
      </template>
      <el-table :data="statuses" size="small" border>
        <el-table-column label="协议" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip placement="top" :show-after="300">
              <template #content>
                <div class="tip-block">ID: {{ row.protocolId }}</div>
                <div v-if="row.statusMessage" class="tip-block">{{ row.statusMessage }}</div>
              </template>
              <span class="name-cell">{{ row.protocolName || ('协议 #' + row.protocolId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'success' : (row.status === 'ERROR' ? 'danger' : 'info')">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip placement="top" :show-after="300">
              <template #content>
                <pre class="tip-pre">{{ configTooltip(row.sourceDesc, row.sourceConfig) }}</pre>
              </template>
              <span class="name-cell">{{ row.sourceName || row.sourceDesc }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="输出目标" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip placement="top" :show-after="300">
              <template #content>
                <pre class="tip-pre">{{ configTooltip(row.sinkDesc, row.sinkConfig) }}</pre>
              </template>
              <span class="name-cell">{{ row.sinkName || row.sinkDesc }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="in" label="接收" width="90" />
        <el-table-column prop="out" label="输出" width="90" />
        <el-table-column label="最后一次转发时间" width="170">
          <template #default="{ row }">
            <span :class="lastForwardClass(row.lastForwardAtMs)">
              {{ formatLastForward(row.lastForwardAtMs) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scriptError" label="脚本错误" width="90" />
        <el-table-column prop="timeout" label="超时" width="80" />
        <el-table-column label="缓冲水位" width="140">
          <template #default="{ row }">
            <el-progress :percentage="bufferUsage(row)" :status="bufferUsage(row) > 80 ? 'warning' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="avgCostMs" label="平均耗时(ms)" width="110">
          <template #default="{ row }">{{ row.avgCostMs?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="lastError" label="最后错误" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, Overview, RuntimeStatus } from '../api'
import ResourceDonutChart, { type DonutSegment } from '../components/ResourceDonutChart.vue'

const COLOR_REMAINING = '#c0c4cc'
const COLOR_PROCESS = '#1d39c4'
const COLOR_OTHER = '#79bbff'

const overview = ref<Overview>({
  runningCount: 0,
  totalIn: 0,
  totalOut: 0,
  totalScriptError: 0,
  totalTimeout: 0,
  totalSinkError: 0,
  logQueueSize: 0,
  recordQueueSize: 0,
  droppedLogs: 0,
  droppedRecords: 0
})
const statuses = ref<RuntimeStatus[]>([])
const auto = ref(true)
let timer: ReturnType<typeof setInterval> | null = null

const ONE_HOUR_MS = 60 * 60 * 1000

const sys = computed(() => overview.value.systemResource ?? {
  cpuRemainingPercent: 0,
  cpuProcessPercent: 0,
  cpuOtherPercent: 0,
  memTotalBytes: 0,
  memFreeBytes: 0,
  memProcessBytes: 0,
  memOtherBytes: 0,
  available: false
})

const cpuCenterText = computed(() => {
  if (!sys.value.available) return 'N/A'
  const used = sys.value.cpuProcessPercent + sys.value.cpuOtherPercent
  return `${used.toFixed(1)}%`
})

const memCenterText = computed(() => {
  if (!sys.value.available || !sys.value.memTotalBytes) return 'N/A'
  const used = sys.value.memTotalBytes - sys.value.memFreeBytes
  return formatBytes(used)
})

const cpuSegments = computed((): DonutSegment[] => [
  {
    label: '剩余 CPU',
    value: sys.value.cpuRemainingPercent,
    color: COLOR_REMAINING,
    display: `${sys.value.cpuRemainingPercent.toFixed(1)}%`
  },
  {
    label: '本服务 CPU',
    value: sys.value.cpuProcessPercent,
    color: COLOR_PROCESS,
    display: `${sys.value.cpuProcessPercent.toFixed(1)}%`
  },
  {
    label: '其他 CPU',
    value: sys.value.cpuOtherPercent,
    color: COLOR_OTHER,
    display: `${sys.value.cpuOtherPercent.toFixed(1)}%`
  }
])

const memSegments = computed((): DonutSegment[] => [
  {
    label: '空闲内存',
    value: sys.value.memFreeBytes,
    color: COLOR_REMAINING,
    display: formatBytes(sys.value.memFreeBytes)
  },
  {
    label: '本服务内存',
    value: sys.value.memProcessBytes,
    color: COLOR_PROCESS,
    display: formatBytes(sys.value.memProcessBytes)
  },
  {
    label: '其他内存',
    value: sys.value.memOtherBytes,
    color: COLOR_OTHER,
    display: formatBytes(sys.value.memOtherBytes)
  }
])

function formatBytes(bytes: number) {
  if (!bytes || bytes < 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatLastForward(ts?: number | null) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function lastForwardClass(ts?: number | null) {
  if (!ts) return 'last-forward-none'
  return Date.now() - ts <= ONE_HOUR_MS ? 'last-forward-ok' : 'last-forward-stale'
}

function bufferUsage(row: RuntimeStatus) {
  if (!row.bufferSize) return 0
  return Math.round(((row.bufferSize - row.bufferRemaining) / row.bufferSize) * 100)
}

function configTooltip(desc: string, config?: string) {
  const lines: string[] = []
  if (desc) lines.push(desc)
  if (config) {
    try {
      lines.push(JSON.stringify(JSON.parse(config), null, 2))
    } catch {
      lines.push(config)
    }
  }
  return lines.join('\n\n') || '-'
}

async function refresh() {
  try {
    overview.value = await api.overview()
    statuses.value = await api.allStatus()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(() => { if (auto.value) refresh() }, 5000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.stat { text-align: center; padding: 6px 0; }
.stat .num { font-size: 26px; font-weight: 700; }
.stat .num.err { color: #f56c6c; }
.stat .label { color: #909399; margin-top: 4px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name-cell { cursor: default; }
.tip-pre {
  margin: 0;
  max-width: 480px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.4;
}
.tip-block { max-width: 320px; line-height: 1.4; }
.last-forward-ok { color: #67c23a; font-weight: 500; }
.last-forward-stale { color: #f56c6c; font-weight: 500; }
.last-forward-none { color: #909399; }
</style>
