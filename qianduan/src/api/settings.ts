import request from './request'
import { USE_REAL_API } from '../config/env'
import { useSettingsStore, type SystemSettings } from '../stores/settings'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function getSystemSettings(): Promise<ApiResponse<SystemSettings>> {
  if (USE_REAL_API) {
    return request.get('/settings')
  }

  const settingsStore = useSettingsStore()

  return {
    code: 20000,
    message: 'success',
    data: settingsStore.settings
  }
}

export async function saveSystemSettings(
  settings: SystemSettings
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post('/settings', settings)
  }

  const settingsStore = useSettingsStore()
  settingsStore.saveSettings(settings)

  return {
    code: 20000,
    message: '保存成功',
    data: null
  }
}