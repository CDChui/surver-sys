<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { oauthCallbackLogin } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { buildLoginPathWithRedirect, getPostLoginPath } from '../../utils/auth-redirect'
import { appendOperationLog } from '../../utils/log'
import { consumeOauthLoginState } from '../../utils/oauth-login'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

const loading = ref(true)
const errorText = ref('')
const providerName = ref('')

function getQueryString(key: string) {
  const raw = route.query[key]
  if (Array.isArray(raw)) return String(raw[0] || '').trim()
  return String(raw || '').trim()
}

function handleError(message: string) {
  errorText.value = message
  loading.value = false
}

function goLocalLogin() {
  const fallbackPath = getQueryString('redirect') || '/admin/dashboard'
  router.push(buildLoginPathWithRedirect(fallbackPath))
}

function goHome() {
  router.push('/m')
}

onMounted(async () => {
  try {
    const oauthError = getQueryString('error')
    if (oauthError) {
      handleError(`第三方认证失败：${oauthError}`)
      return
    }

    const code = getQueryString('code')
    const state = getQueryString('state')

    if (!code || !state) {
      handleError('缺少回调参数，请重新发起登录。')
      return
    }

    const statePayload = consumeOauthLoginState(state)

    if (!statePayload) {
      handleError('登录状态已失效，请重新发起登录。')
      return
    }

    const provider =
      settingsStore.settings.authIntegration.providers.find(
        (item) => item.id === statePayload.providerId
      ) || { id: statePayload.providerId, name: statePayload.providerId }

    if (!provider) {
      handleError('认证平台配置不存在，请联系管理员。')
      return
    }

    providerName.value = provider.name

    const response = await oauthCallbackLogin({
      providerId: provider.id,
      code,
      state,
      redirectPath: statePayload.redirectPath
    })

    if (response.code !== 20000) {
      handleError(response.message || '第三方登录失败，请稍后重试。')
      return
    }

    authStore.setAuth(response.data)
    appendOperationLog({
      module: 'SYSTEM',
      action: 'LOGIN',
      target: `第三方登录（${provider.name}）`
    })

    const targetPath = getPostLoginPath(response.data.role, statePayload.redirectPath)
    await router.replace(targetPath)
  } catch (error) {
    handleError('回调处理失败，请稍后重试。')
  } finally {
    if (!errorText.value) {
      loading.value = false
    }
  }
})
</script>

<template>
  <div
    style="
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f7fa;
      padding: 16px;
    "
  >
    <div
      style="
        width: 100%;
        max-width: 560px;
        background: #fff;
        border-radius: 16px;
        padding: 32px 24px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.08);
      "
    >
      <template v-if="loading">
        <div style="text-align: center;">
          <el-icon class="is-loading" style="font-size: 28px; margin-bottom: 12px;">
            <Loading />
          </el-icon>
          <div style="font-size: 18px; font-weight: 700; margin-bottom: 8px;">
            正在处理第三方登录
          </div>
          <div style="color: #666;">
            {{ providerName ? `认证平台：${providerName}` : '请稍候...' }}
          </div>
        </div>
      </template>

      <template v-else>
        <div style="text-align: center;">
          <div style="font-size: 18px; font-weight: 700; margin-bottom: 10px; color: #f56c6c;">
            第三方登录失败
          </div>
          <div style="color: #666; margin-bottom: 20px;">
            {{ errorText || '回调处理失败，请稍后重试。' }}
          </div>

          <div style="display: flex; justify-content: center; gap: 12px;">
            <el-button type="primary" @click="goLocalLogin">
              使用本地登录
            </el-button>
            <el-button @click="goHome">
              返回首页
            </el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
