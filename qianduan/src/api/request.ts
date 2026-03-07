import axios from 'axios'
import { API_BASE } from '../config/env'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 10000
})

function getCurrentSurveyIdFromPath() {
  const match = window.location.pathname.match(/\/m\/surveys\/(\d+)/)
  return match?.[1] || ''
}

function redirectTo(path: string) {
  if (window.location.pathname !== path) {
    window.location.href = path
  }
}

function handleBusinessCode(code: number, message: string) {
  const surveyId = getCurrentSurveyIdFromPath()

  if (code === 40101 || code === 40102) {
    redirectTo('/local-admin/login')
    return Promise.reject(new Error(message || '登录已失效'))
  }

  if (code === 40009) {
    redirectTo(`/m/blocked/duplicate?id=${surveyId || 1}`)
    return Promise.reject(new Error(message || '你已提交过该问卷'))
  }

  if (code === 40011) {
    redirectTo('/m/blocked/quota')
    return Promise.reject(new Error(message || '当前问卷名额已满'))
  }

  if (code === 40001) {
    return Promise.reject(new Error(message || '请求参数错误'))
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
        const handled = handleBusinessCode(data.code, data.message || '')
        if (handled) return handled
      }

      return data
    }

    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      redirectTo('/local-admin/login')
    }

    return Promise.reject(error)
  }
)

export default request