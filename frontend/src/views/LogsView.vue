<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>日志查询</span>
        <el-button size="small" type="warning" @click="purge">清理过期分区</el-button>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="query">
      <el-tab-pane label="运行日志" name="logs">
        <div class="filters">
          <el-select v-model="filter.protocolId" placeholder="协议" clearable style="width: 180px">
            <el-option v-for="p in protocols" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="filter.level" placeholder="级别" clearable style="width: 120px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
          <el-input v-model="filter.keyword" placeholder="关键字" clearable style="width: 200px" />
          <el-button type="primary" @click="query">查询</el-button>
        </div>
        <el-table :data="logRows" size="small" border>
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="protocolId" label="协议" width="80" />
          <el-table-column prop="level" label="级别" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="tagType(row.level)">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="logTime" label="时间" width="220" />
          <el-table-column prop="message" label="内容" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="转发明细" name="records">
        <div class="filters">
          <el-select v-model="filter.protocolId" placeholder="协议" clearable style="width: 180px">
            <el-option v-for="p in protocols" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="filter.success" placeholder="结果" clearable style="width: 120px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
          <el-button type="primary" @click="query">查询</el-button>
        </div>
        <el-table :data="recordRows" size="small" border>
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="protocolId" label="协议" width="80" />
          <el-table-column prop="success" label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? 'OK' : 'FAIL' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时ms" width="90" />
          <el-table-column prop="input" label="输入" show-overflow-tooltip />
          <el-table-column prop="output" label="输出" show-overflow-tooltip />
          <el-table-column prop="recordTime" label="时间" width="220" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="onPage"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, Protocol } from '../api'

const tab = ref('logs')
const protocols = ref<Protocol[]>([])
const logRows = ref<any[]>([])
const recordRows = ref<any[]>([])
const total = ref(0)
const page = ref(0)
const size = 50
const filter = reactive<any>({ protocolId: undefined, level: undefined, keyword: '', success: undefined })

const tagType = (lv: string) => (lv === 'ERROR' ? 'danger' : lv === 'WARN' ? 'warning' : lv === 'DEBUG' ? 'info' : 'success')

async function query() {
  const base: any = { protocolId: filter.protocolId, page: page.value, size }
  if (tab.value === 'logs') {
    const res = await api.queryLogs({ ...base, level: filter.level, keyword: filter.keyword })
    logRows.value = res.items
    total.value = res.total
  } else {
    const res = await api.queryRecords({ ...base, success: filter.success })
    recordRows.value = res.items
    total.value = res.total
  }
}

function onPage(p: number) {
  page.value = p - 1
  query()
}

async function purge() {
  await api.purgeLogs()
  ElMessage.success('已触发清理')
}

onMounted(async () => {
  protocols.value = await api.listProtocols()
  query()
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 10px; margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
