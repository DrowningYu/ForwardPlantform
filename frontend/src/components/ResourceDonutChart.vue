<template>
  <div class="donut-card">
    <div class="donut-wrap">
      <div
        class="donut"
        :style="{ background: conicGradient }"
      />
      <div class="donut-hole">
        <div class="center-title">{{ title }}</div>
        <div class="center-value">{{ centerText }}</div>
      </div>
    </div>
    <div class="legend">
      <div v-for="item in legendItems" :key="item.label" class="legend-row">
        <span class="dot" :style="{ background: item.color }" />
        <span class="legend-label">{{ item.label }}</span>
        <span class="legend-value">{{ item.display }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface DonutSegment {
  label: string
  value: number
  color: string
  display: string
}

const props = defineProps<{
  title: string
  centerText: string
  segments: DonutSegment[]
  unavailable?: boolean
}>()

const legendItems = computed(() => props.segments)

const conicGradient = computed(() => {
  if (props.unavailable) {
    return 'conic-gradient(#dcdfe6 0% 100%)'
  }
  const total = props.segments.reduce((sum, s) => sum + s.value, 0)
  if (total <= 0) {
    return 'conic-gradient(#dcdfe6 0% 100%)'
  }
  let cursor = 0
  const stops: string[] = []
  for (const seg of props.segments) {
    if (seg.value <= 0) continue
    const start = (cursor / total) * 100
    cursor += seg.value
    const end = (cursor / total) * 100
    stops.push(`${seg.color} ${start}% ${end}%`)
  }
  return `conic-gradient(${stops.join(', ')})`
})
</script>

<style scoped>
.donut-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 4px;
}
.donut-wrap {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}
.donut {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}
.donut-hole {
  position: absolute;
  inset: 22px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 4px;
}
.center-title {
  font-size: 12px;
  color: #909399;
}
.center-value {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
  margin-top: 2px;
  line-height: 1.2;
}
.legend {
  flex: 1;
  min-width: 0;
}
.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.8;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-label {
  color: #606266;
  flex: 1;
}
.legend-value {
  color: #303133;
  font-weight: 500;
  white-space: nowrap;
}
</style>
