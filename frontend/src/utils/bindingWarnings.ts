import type { BindingCheckResult } from '../api'

export function formatBindingMessages(result: BindingCheckResult): string {
  const items = [...(result.blockers || []), ...(result.warnings || [])]
  return items.map((w) => w.message).join('\n\n')
}

export function mergeBindingLevel(result?: BindingCheckResult | null): 'BLOCK' | 'WARN' | null {
  if (!result) return null
  if (result.blockers?.length) return 'BLOCK'
  if (result.warnings?.length) return 'WARN'
  return null
}
