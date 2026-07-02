<template>
  <el-tooltip v-if="level" :content="tooltip" placement="top" :show-after="200">
    <el-icon class="binding-warn-icon" :class="level === 'BLOCK' ? 'is-block' : 'is-warn'">
      <WarningFilled />
    </el-icon>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import type { BindingCheckResult } from '../api'

const props = defineProps<{
  result?: BindingCheckResult | null
}>()

const level = computed<'BLOCK' | 'WARN' | null>(() => {
  if (!props.result) return null
  if (props.result.blockers?.length) return 'BLOCK'
  if (props.result.warnings?.length) return 'WARN'
  return null
})

const tooltip = computed(() => {
  if (!props.result) return ''
  const items = [...(props.result.blockers || []), ...(props.result.warnings || [])]
  return items.map((w) => w.message).join('\n\n')
})
</script>

<style scoped>
.binding-warn-icon {
  margin-left: 6px;
  vertical-align: middle;
  font-size: 14px;
}
.binding-warn-icon.is-block {
  color: var(--el-color-danger);
}
.binding-warn-icon.is-warn {
  color: var(--el-color-warning);
}
</style>
