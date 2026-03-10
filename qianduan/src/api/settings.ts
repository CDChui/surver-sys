import request from './request'
import { USE_REAL_API } from '../config/env'
import {
  useSettingsStore,
  type AuthIntegrationSettings,
  type SystemSettings
} from '../stores/settings'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PublicSystemBranding {
  systemName: string
  adminLogo: string
  userHomeLogo: string
}

export interface PublicAuthSettingsData {
  authIntegration: AuthIntegrationSettings
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

export async function getPublicSystemBranding(): Promise<ApiResponse<PublicSystemBranding>> {
  if (USE_REAL_API) {
    return request.get('/settings/public')
  }

  const settingsStore = useSettingsStore()

  return {
    code: 20000,
    message: 'success',
    data: {
      systemName: settingsStore.settings.systemName,
      adminLogo: settingsStore.settings.adminLogo,
      userHomeLogo: settingsStore.settings.userHomeLogo
    }
  }
}

export async function getPublicAuthSettings(): Promise<ApiResponse<PublicAuthSettingsData>> {
  if (USE_REAL_API) {
    return request.get('/settings/public-auth', {
      headers: {
        'X-Skip-Auth-Redirect': '1'
      }
    })
  }

  const settingsStore = useSettingsStore()

  return {
    code: 20000,
    message: 'success',
    data: {
      authIntegration: settingsStore.settings.authIntegration
    }
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
