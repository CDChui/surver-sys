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