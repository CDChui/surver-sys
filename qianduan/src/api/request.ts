import axios from 'axios'
import { API_BASE } from '../config/env'
import { buildLoginPathWithRedirect } from '../utils/auth-redirect'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 10000
})

function shouldSkipAuthRedirectByConfig(config: unknown) {
  if (!config || typeof config !== 'object') return false

  const requestConfig = config as {
    url?: unknown
    headers?: Record<string, unknown>
  }

  const headers = requestConfig.headers || {}
  const skipHeader =
    headers['X-Skip-Auth-Redirect'] ?? headers['x-skip-auth-redirect']

  if (skipHeader === '1' || skipHeader === 1 || skipHeader === true) {
    return true
  }

  const requestUrl = String(requestConfig.url || '')
  return requestUrl.includes('/settings/public-auth')
}

function getCurrentSurveyIdFromPath() {
  const match = window.location.pathname.match(/\/m\/surveys\/(\d+)/)
  return match?.[1] || ''
}

function toSingleValue(raw: unknown) {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

function isTruthyPreviewFlag(raw: unknown) {
  const value = String(toSingleValue(raw) ?? '')
    .trim()
    .toLowerCase()
  return value === '1' || value === 'true' || value === 'yes'
}

function isPreviewModeFromConfig(requestConfig?: unknown) {
  if (!requestConfig || typeof requestConfig !== 'object') {
    return isPreviewModeFromLocation()
  }

  const config = requestConfig as {
    params?: Record<string, unknown>
    data?: unknown
    url?: unknown
  }

  if (
    isTruthyPreviewFlag(config.params?.previewMode) ||
    isTruthyPreviewFlag(config.params?.preview)
  ) {
    return true
  }

  const rawData = config.data
  if (rawData && typeof rawData === 'object') {
    const data = rawData as Record<string, unknown>
    if (
      isTruthyPreviewFlag(data.previewMode) ||
      isTruthyPreviewFlag(data.preview)
    ) {
      return true
    }
  }

  if (typeof rawData === 'string') {
    try {
      const parsed = JSON.parse(rawData) as Record<string, unknown>
      if (
        isTruthyPreviewFlag(parsed.previewMode) ||
        isTruthyPreviewFlag(parsed.preview)
      ) {
        return true
      }
    } catch (error) {
      // ignore invalid json payload
    }
  }

  const urlText = String(config.url || '')
  if (urlText) {
    try {
      const parsedUrl = new URL(urlText, window.location.origin)
      if (
        isTruthyPreviewFlag(parsedUrl.searchParams.get('previewMode')) ||
        isTruthyPreviewFlag(parsedUrl.searchParams.get('preview'))
      ) {
        return true
      }
    } catch (error) {
      // ignore invalid url
    }
  }

  return isPreviewModeFromLocation()
}

function isPreviewModeFromLocation() {
  try {
    const params = new URLSearchParams(window.location.search || '')
    return (
      isTruthyPreviewFlag(params.get('previewMode')) ||
      isTruthyPreviewFlag(params.get('preview'))
    )
  } catch (error) {
    return false
  }
}

function redirectTo(path: string) {
  const currentPath = `${window.location.pathname}${window.location.search}`

  if (currentPath !== path) {
    window.location.href = path
  }
}

function clearAuthStorage() {
  localStorage.removeItem('AUTH_TOKEN')
  localStorage.removeItem('AUTH_ROLE')
  localStorage.removeItem('AUTH_USERNAME')
  localStorage.removeItem('AUTH_REALNAME')
  localStorage.removeItem('AUTH_USER_ID')
  localStorage.removeItem('AUTH_LOCAL_ACCOUNT')
}

function shouldSkipLoginRedirect(pathname: string) {
  return (
    pathname.startsWith('/auth/logout') ||
    pathname.startsWith('/local-admin/login') ||
    pathname.startsWith('/auth/sso/callback')
  )
}

function redirectToLoginEntryByCurrentPath() {
  const currentPath = `${window.location.pathname}${window.location.search}`
  const isAdminPath = window.location.pathname.startsWith('/admin')

  clearAuthStorage()

  if (shouldSkipLoginRedirect(window.location.pathname)) {
    return
  }

  if (isAdminPath) {
    window.location.href = currentPath
    return
  }

  redirectTo(buildLoginPathWithRedirect(currentPath))
}

function handleBusinessCode(code: number, message: string, requestConfig?: unknown) {
  const surveyId = getCurrentSurveyIdFromPath()

  if (code === 40101 || code === 40102) {
    if (shouldSkipAuthRedirectByConfig(requestConfig)) {
      return Promise.reject(new Error(message || 'Unauthorized'))
    }

    redirectToLoginEntryByCurrentPath()
    return Promise.reject(new Error(message || 'Login expired'))
  }

  if (code === 40009) {
    if (isPreviewModeFromConfig(requestConfig)) {
      return null
    }
    redirectTo(`/m/blocked/duplicate?id=${surveyId || 1}`)
    return Promise.reject(new Error(message || 'Duplicate submit'))
  }

  if (code === 40011) {
    redirectTo('/m/blocked/quota')
    return Promise.reject(new Error(message || 'Survey quota full'))
  }

  if (code === 40001) {
    return Promise.reject(new Error(message || 'Invalid request params'))
  }

  return null
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('AUTH_TOKEN')

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const data = response.data

    if (
      data &&
      typeof data === 'object' &&
      'code' in data &&
      typeof data.code === 'number'
    ) {
      if (data.code !== 20000) {
        const handled = handleBusinessCode(
          data.code,
          typeof data.message === 'string' ? data.message : '',
          response.config
        )
        if (handled) return handled
      }

      return data
    }

    return data
  },
  (error) => {
    if (
      error.response?.status === 401 &&
      !shouldSkipAuthRedirectByConfig(error.config)
    ) {
      redirectToLoginEntryByCurrentPath()
    }

    return Promise.reject(error)
  }
)

export default request
