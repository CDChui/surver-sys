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
}

const STORAGE_KEY = 'SYSTEM_LOG_LIST'

function getDefaultLogs(): LogItem[] {
  return [
    {
      id: 1,
      operator: 'admin',
      module: 'SURVEY',
      action: 'CREATE',
      target: '2026届毕业生满意度调查',
      createdAt: '2026-03-07 09:00:00'
    },
    {
      id: 2,
      operator: 'admin',
      module: 'SURVEY',
      action: 'PUBLISH',
      target: '食堂服务评价问卷',
      createdAt: '2026-03-07 10:20:00'
    },
    {
      id: 3,
      operator: 'teacher01',
      module: 'USER',
      action: 'UPDATE',
      target: '张老师',
      createdAt: '2026-03-07 11:05:00'
    },
    {
      id: 4,
      operator: 'admin',
      module: 'PERMISSION',
      action: 'UPDATE',
      target: 'ROLE3 权限配置',
      createdAt: '2026-03-07 13:40:00'
    },
    {
      id: 5,
      operator: 'admin',
      module: 'SYSTEM',
      action: 'LOGIN',
      target: '后台登录',
      createdAt: '2026-03-07 14:00:00'
    },
    {
      id: 6,
      operator: 'admin',
      module: 'SURVEY',
      action: 'DELETE',
      target: '校园活动反馈表',
      createdAt: '2026-03-07 15:30:00'
    }
  ]
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultLogs()))
  }
}

function readLogs(): LogItem[] {
  ensureStorage()
  return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
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
          id: Date.now(),
          ...log
        },
        ...this.logList
      ]
      this.persist()
    }
  }
})