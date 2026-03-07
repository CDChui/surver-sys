import { defineStore } from 'pinia'

export interface SystemSettings {
  systemName: string
  defaultPageSize: number
  enableLog: boolean
  enableResumeDraft: boolean
  allowDuplicateSubmit: boolean
}

const STORAGE_KEY = 'SYSTEM_SETTINGS'

function getDefaultSettings(): SystemSettings {
  return {
    systemName: '问卷调查系统',
    defaultPageSize: 5,
    enableLog: true,
    enableResumeDraft: true,
    allowDuplicateSubmit: false
  }
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultSettings()))
  }
}

function readSettings(): SystemSettings {
  ensureStorage()
  return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
}

function writeSettings(settings: SystemSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
}

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    settings: readSettings() as SystemSettings
  }),

  actions: {
    refreshFromStorage() {
      this.settings = readSettings()
    },

    saveSettings(newSettings: SystemSettings) {
      this.settings = { ...newSettings }
      writeSettings(this.settings)
    }
  }
})