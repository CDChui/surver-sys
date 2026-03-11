import { defineStore } from 'pinia'

export type LogModule = 'SURVEY' | 'USER' | 'PERMISSION' | 'SYSTEM'
export type LogAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'PUBLISH'
  | 'CLOSE'
  | 'LOGIN'
  | 'LOGOUT'

export interface LogItem {
  id: number
  operator: string
  module: LogModule
  action: LogAction
  target: string
  createdAt: string
  terminalType?: string
  sourceIp?: string
}

const STORAGE_KEY = 'SYSTEM_LOG_LIST'
const LEGACY_MOCK_LOG_SIGNATURES = new Set([
  'admin|SURVEY|CREATE|2026届毕业生满意度调查|2026-03-07 09:00:00',
  'admin|SURVEY|PUBLISH|食堂服务评价问卷|2026-03-07 10:20:00',
  'teacher01|USER|UPDATE|张老师|2026-03-07 11:05:00',
  'admin|PERMISSION|UPDATE|ROLE3 权限配置|2026-03-07 13:40:00',
  'admin|SYSTEM|LOGIN|后台登录|2026-03-07 14:00:00',
  'admin|SURVEY|DELETE|校园活动反馈表|2026-03-07 15:30:00'
])

function getDefaultLogs(): LogItem[] {
  return []
}

function getLogSignature(log: LogItem) {
  return `${log.operator}|${log.module}|${log.action}|${log.target}|${log.createdAt}`
}

function removeLegacyMockLogs(logs: LogItem[]) {
  return logs.filter((item) => !LEGACY_MOCK_LOG_SIGNATURES.has(getLogSignature(item)))
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultLogs()))
  }
}

function readLogs(): LogItem[] {
  ensureStorage()
  const logs = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]') as unknown
  if (!Array.isArray(logs)) return []

  const normalized = logs as LogItem[]
  const cleaned = removeLegacyMockLogs(normalized)

  if (cleaned.length !== normalized.length) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cleaned))
  }

  return cleaned
}

function writeLogs(logs: LogItem[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(logs))
}

export const useLogStore = defineStore('log', {
  state: () => ({
    logList: readLogs() as LogItem[]
  }),

  actions: {
    refreshFromStorage() {
      this.logList = readLogs()
    },

    persist() {
      writeLogs(this.logList)
    },

    addLog(log: Omit<LogItem, 'id'>) {
      this.logList = [
        {
          id: Date.now() + Math.floor(Math.random() * 1000),
          ...log
        },
        ...this.logList
      ]
      this.persist()
    }
  }
})
