import request from './request'
import { USE_REAL_API } from '../config/env'
import { useLogStore } from '../stores/log'
import { useUserStore } from '../stores/user'

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

export type LogType = 'SYSTEM' | 'USER'

export interface LogItemResult {
  id: number
  operator: string
  module: LogModule
  action: LogAction
  target: string
  createdAt: string
  terminalType?: string
  sourceIp?: string
}

export interface LogListParams {
  logType?: LogType
  order?: 'ASC' | 'DESC'
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

function resolveLogType(operator: string): LogType {
  const userStore = useUserStore()
  const match = userStore.userList.find((item) => item.username === operator)
  return match?.role === 'ROLE1' ? 'USER' : 'SYSTEM'
}

function sortLogs(items: LogItemResult[], order: 'ASC' | 'DESC') {
  const direction = order === 'ASC' ? 1 : -1
  return [...items].sort((a, b) => {
    if (a.createdAt === b.createdAt) {
      return (a.id - b.id) * direction
    }
    return a.createdAt > b.createdAt ? direction : -direction
  })
}

export async function getLogList(
  params: LogListParams = {}
): Promise<ApiResponse<LogItemResult[]>> {
  if (USE_REAL_API) {
    return request.get('/logs', {
      params: {
        type: params.logType,
        order: params.order || 'DESC'
      }
    })
  }

  const logStore = useLogStore()
  const logType = params.logType
  const order = params.order || 'DESC'

  let data = logStore.logList as LogItemResult[]
  if (logType) {
    data = data.filter((item) => resolveLogType(item.operator) === logType)
  }

  data = sortLogs(data, order)

  return {
    code: 20000,
    message: 'success',
    data
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
    createdAt: params.createdAt || getNowText(),
    terminalType: '未知',
    sourceIp: ''
  })

  return {
    code: 20000,
    message: 'success',
    data: null
  }
}
