import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.response.use(
  (resp) => resp.data,
  (err: AxiosError<any>) => {
    const code = err?.response?.data?.code
    if (code === 'BINDING_BLOCKED' || code === 'BINDING_WARNING') {
      return Promise.reject(err)
    }
    const msg = err?.response?.data?.error || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export interface ConfigCarrier {
  id: number
  name: string
  type: string
  config: any
  createdAt?: string
  updatedAt?: string
}

export interface Protocol {
  id: number
  name: string
  description?: string
  status: string
  statusMessage?: string
  enabled: boolean
  running: boolean
  sourceId?: number
  outputTargetId?: number
  currentVersionId?: number
  ringBufferSize: number
  workerThreads: number
  logRetentionDays: number
  sampleRate: number
}

export interface BindingWarning {
  code: string
  level: 'BLOCK' | 'WARN'
  resourceRole: 'SOURCE' | 'SINK'
  resourceName: string
  message: string
  relatedProtocolNames: string[]
}

export interface BindingCheckResult {
  blockers: BindingWarning[]
  warnings: BindingWarning[]
}

export interface RuntimeStatus {
  protocolId: number
  protocolName?: string
  status: string
  statusMessage?: string
  sourceName?: string
  sourceConfig?: string
  sourceDesc: string
  sinkName?: string
  sinkConfig?: string
  sinkDesc: string
  in: number
  processed: number
  out: number
  scriptError: number
  timeout: number
  sinkError: number
  avgCostMs: number
  bufferSize: number
  bufferRemaining: number
  lastError?: string
  /** 最近一次成功转发时间（epoch ms） */
  lastForwardAtMs?: number | null
}

export const api = {
  // 数据源
  listDataSources: (): Promise<ConfigCarrier[]> => http.get('/data-sources'),
  createDataSource: (body: any): Promise<ConfigCarrier> => http.post('/data-sources', body),
  updateDataSource: (id: number, body: any): Promise<ConfigCarrier> => http.put(`/data-sources/${id}`, body),
  deleteDataSource: (id: number) => http.delete(`/data-sources/${id}`),

  // 输出目标
  listOutputTargets: (): Promise<ConfigCarrier[]> => http.get('/output-targets'),
  createOutputTarget: (body: any): Promise<ConfigCarrier> => http.post('/output-targets', body),
  updateOutputTarget: (id: number, body: any): Promise<ConfigCarrier> => http.put(`/output-targets/${id}`, body),
  deleteOutputTarget: (id: number) => http.delete(`/output-targets/${id}`),

  // 协议
  listProtocols: (): Promise<Protocol[]> => http.get('/protocols'),
  getProtocol: (id: number): Promise<Protocol> => http.get(`/protocols/${id}`),
  createProtocol: (body: any): Promise<Protocol> => http.post('/protocols', body),
  updateProtocol: (id: number, body: any): Promise<Protocol> => http.put(`/protocols/${id}`, body),
  deleteProtocol: (id: number) => http.delete(`/protocols/${id}`),
  getBindingWarnings: (params: {
    protocolId?: number
    sourceId?: number
    outputTargetId?: number
  }): Promise<BindingCheckResult> => http.get('/protocols/binding-warnings', { params }),
  checkProtocolStart: (id: number): Promise<BindingCheckResult> =>
    http.get(`/protocols/${id}/start-check`),
  startProtocol: (id: number, opts?: { acknowledgeWarnings?: boolean }): Promise<Protocol> =>
    http.post(`/protocols/${id}/start`, null, {
      params: { acknowledgeWarnings: opts?.acknowledgeWarnings ?? false }
    }),
  stopProtocol: (id: number): Promise<Protocol> => http.post(`/protocols/${id}/stop`),
  restartProtocol: (id: number, opts?: { acknowledgeWarnings?: boolean }): Promise<Protocol> =>
    http.post(`/protocols/${id}/restart`, null, {
      params: { acknowledgeWarnings: opts?.acknowledgeWarnings ?? false }
    }),

  // 脚本
  listVersions: (pid: number): Promise<any[]> => http.get(`/protocols/${pid}/scripts`),
  currentScript: (pid: number): Promise<any> => http.get(`/protocols/${pid}/scripts/current`),
  getVersion: (pid: number, vid: number): Promise<any> => http.get(`/protocols/${pid}/scripts/${vid}`),
  saveScript: (pid: number, body: any): Promise<any> => http.post(`/protocols/${pid}/scripts`, body),
  activateVersion: (pid: number, vid: number): Promise<any> => http.post(`/protocols/${pid}/scripts/${vid}/activate`),

  // 调试
  debugRun: (body: any): Promise<any> => http.post('/debug/run', body),
  debugCapture: (body: any): Promise<any> => http.post('/debug/capture', body),

  // 日志
  queryLogs: (params: any): Promise<any> => http.get('/logs', { params }),
  queryRecords: (params: any): Promise<any> => http.get('/logs/records', { params }),
  purgeLogs: (): Promise<any> => http.post('/logs/purge'),

  // 状态
  allStatus: (): Promise<RuntimeStatus[]> => http.get('/status'),
  overview: (): Promise<any> => http.get('/status/overview')
}
