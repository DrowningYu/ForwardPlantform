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

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>协议运行状态</span>
          <el-switch v-model="auto" active-text="自动刷新(5s)" />
        </div>
      </template>
      <el-table :data="statuses" size="small" border>
        <el-table-column prop="protocolId" label="协议ID" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'success' : (row.status === 'ERROR' ? 'danger' : 'info')">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceDesc" label="数据源" show-overflow-tooltip />
        <el-table-column prop="sinkDesc" label="输出目标" show-overflow-tooltip />
        <el-table-column prop="in" label="接收" width="90" />
        <el-table-column prop="out" label="输出" width="90" />
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
import { onMounted, onUnmounted, ref } from 'vue'
import { api, RuntimeStatus } from '../api'

const overview = ref<any>({})
const statuses = ref<RuntimeStatus[]>([])
const auto = ref(true)
let timer: any = null

function bufferUsage(row: RuntimeStatus) {
  if (!row.bufferSize) return 0
  return Math.round(((row.bufferSize - row.bufferRemaining) / row.bufferSize) * 100)
}

async function refresh() {
  try {
    overview.value = await api.overview()
    statuses.value = await api.allStatus()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(() => { if (auto.value) refresh() }, 5000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.stat { text-align: center; padding: 6px 0; }
.stat .num { font-size: 26px; font-weight: 700; }
.stat .num.err { color: #f56c6c; }
.stat .label { color: #909399; margin-top: 4px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
