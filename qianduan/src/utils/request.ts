import axios from 'axios'
import { useAuthStore } from '../stores/auth'

export const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()

    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 约定接口返回结构：{ code, message, data }
    if (res.code === 20000) {
      return res.data
    }

    alert(res.message || '请求失败')
    return Promise.reject(res)
  },
  (error) => {
    alert('网络错误或服务器无响应')
    return Promise.reject(error)
  }
)