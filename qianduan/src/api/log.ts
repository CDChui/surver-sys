import request from './request'
import { USE_REAL_API } from '../config/env'
import { useLogStore } from '../stores/log'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export type LogModule = 'SURVEY' | 'USER' | 'PERMISSION' | 'SYSTEM'
export type LogAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'PUBLISH'
  | 'CLOSE'
  | 'LOGIN'
  | 'LOGOUT'

export interface LogItemResult {
  id: number
  operator: string
  module: LogModule
  action: LogAction
  target: string
  createdAt: string
}

export interface CreateLogParams {
  operator: string
  module: LogModule
  action: LogAction
  target: string
  createdAt?: string
}

function getNowText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}:${ss}`
}

export async function getLogList(): Promise<ApiResponse<LogItemResult[]>> {
  if (USE_REAL_API) {
    return request.get('/logs')
  }

  const logStore = useLogStore()

  return {
    code: 20000,
    message: 'success',
    data: logStore.logList
  }
}

export async function createLog(params: CreateLogParams): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post('/logs', params)
  }

  const logStore = useLogStore()
  logStore.addLog({
    operator: params.operator,
    module: params.module,
    action: params.action,
    target: params.target,
    createdAt: params.createdAt || getNowText()
  })

  return {
    code: 20000,
    message: 'success',
    data: null
  }
}
