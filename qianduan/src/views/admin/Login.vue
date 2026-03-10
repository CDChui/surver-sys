<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { localLogin } from '../../api/auth'
import { USE_REAL_API } from '../../config/env'
import { useSettingsStore } from '../../stores/settings'
import { appendOperationLog } from '../../utils/log'
import { getPostLoginPath } from '../../utils/auth-redirect'
import {
  beginThirdPartyLogin,
  shouldAutoThirdPartyForAdmin
} from '../../utils/oauth-login'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const oauthLoading = ref(false)
const isRealApi = USE_REAL_API

const canUseThirdPartyLogin = computed(() =>
  shouldAutoThirdPartyForAdmin(settingsStore.settings)
)

const appName = computed(() => settingsStore.settings.systemName || '问卷调查系统')
const adminLogo = computed(() => settingsStore.settings.adminLogo || '')

async function handleLogin() {
  if (!username.value.trim()) {
    alert('请输入用户名')
    return
  }

  if (!password.value.trim()) {
    alert('请输入密码')
    return
  }

  try {
    loading.value = true

    const response = await localLogin({
      username: username.value.trim(),
      password: password.value
    })

    if (response.code !== 20000) {
      alert(response.message || '登录失败')
      return
    }

    const userInfo = response.data
    authStore.setAuth(userInfo)
    appendOperationLog({
      module: 'SYSTEM',
      action: 'LOGIN',
      target: `后台登录（${userInfo.role}）`
    })

    await router.replace(getPostLoginPath(userInfo.role, route.query.redirect))
  } catch (error: unknown) {
    const isNetworkError =
      !!error &&
      typeof error === 'object' &&
      (('code' in error && (error as { code?: string }).code === 'ERR_NETWORK') ||
        ('message' in error &&
          (error as { message?: string }).message === 'Network Error'))

    const isProxy500WhenBackendDown =
      !!error &&
      typeof error === 'object' &&
      'response' in error &&
      (error as { response?: { status?: number; data?: unknown } }).response
        ?.status === 500 &&
      ((!((error as { response?: { data?: unknown } }).response?.data) &&
        'message' in error &&
        (error as { message?: string }).message?.includes('status code 500')) ||
        (typeof (
          (error as { response?: { data?: unknown } }).response?.data
        ) === 'string' &&
          !(
            (error as { response?: { data?: string } }).response?.data || ''
          ).trim()))

    const message = isNetworkError || isProxy500WhenBackendDown
      ? '无法连接后端服务，请确认后端已启动（http://127.0.0.1:8080）。'
      : error instanceof Error
        ? error.message
        : '登录失败'

    alert(message)
  } finally {
    loading.value = false
  }
}

function handleThirdPartyLogin() {
  if (!canUseThirdPartyLogin.value) {
    alert('当前未启用可用的第三方认证平台')
    return
  }

  oauthLoading.value = true

  const result = beginThirdPartyLogin(settingsStore.settings, route.query.redirect)

  if (!result.ok) {
    oauthLoading.value = false
    alert(result.message || '第三方认证跳转失败')
    return
  }

  window.location.href = result.url
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand-block">
        <div class="brand-logo-wrap">
          <img v-if="adminLogo" :src="adminLogo" alt="系统 Logo" class="brand-logo">
          <span v-else class="brand-logo-fallback">LOGO</span>
        </div>
        <h1 class="brand-title">{{ appName }}</h1>
        <p class="brand-subtitle">系统登录</p>
      </div>

      <div class="form-block">
        <div class="form-field">
          <label class="field-label">用户名</label>
          <el-input v-model="username" placeholder="请输入用户名" />
        </div>

        <div class="form-field">
          <label class="field-label">密码</label>
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </div>

        <div v-if="!isRealApi" class="mock-tip">
          mock 账号：`admin` / `teacher01` / `student01`，密码统一 `123456`
        </div>

        <el-button
          type="primary"
          :loading="loading"
          class="submit-btn"
          @click="handleLogin"
        >
          登录
        </el-button>

        <div v-if="canUseThirdPartyLogin" class="oauth-section">
          <div class="oauth-divider">
            <span>或</span>
          </div>
          <el-button
            class="oauth-btn"
            :loading="oauthLoading"
            :disabled="loading"
            @click="handleThirdPartyLogin"
          >
            使用统一身份认证登录
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 24px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(29, 126, 255, 0.12), transparent 45%),
    radial-gradient(circle at 80% 0%, rgba(16, 185, 129, 0.1), transparent 40%),
    #f3f6fb;
}

.login-card {
  width: 100%;
  max-width: 440px;
  border-radius: 18px;
  border: 1px solid #e3ebf7;
  box-shadow: 0 18px 40px rgba(15, 52, 98, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #fdfefe 100%);
  overflow: hidden;
}

.brand-block {
  padding: 24px 24px 18px;
  text-align: center;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  border-bottom: 1px solid #eef3fb;
}

.brand-logo-wrap {
  height: 46px;
  border-radius: 10px;
  border: 1px solid #dce7f5;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  overflow: hidden;
}

.brand-logo {
  max-width: 100%;
  max-height: 36px;
  object-fit: contain;
}

.brand-logo-fallback {
  color: #97a7bf;
  font-size: 12px;
  letter-spacing: 1px;
}

.brand-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.3;
  color: #0f2850;
}

.brand-subtitle {
  margin: 6px 0 0;
  color: #5e718d;
  font-size: 14px;
}

.form-block {
  padding: 22px 24px 24px;
}

.form-field {
  margin-bottom: 14px;
}

.field-label {
  display: block;
  margin-bottom: 7px;
  color: #1f2f46;
  font-weight: 600;
  font-size: 13px;
}

.mock-tip {
  margin: 4px 0 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f6f8fc;
  border: 1px dashed #d8e0ee;
  color: #5f6f86;
  font-size: 12px;
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  height: 40px;
}

.oauth-section {
  margin-top: 14px;
}

.oauth-divider {
  position: relative;
  text-align: center;
  color: #9aa8bd;
  font-size: 12px;
  margin-bottom: 12px;
}

.oauth-divider::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  border-top: 1px solid #e2eaf6;
  transform: translateY(-50%);
}

.oauth-divider span {
  position: relative;
  padding: 0 10px;
  background: #fff;
}

.oauth-btn {
  width: 100%;
  height: 38px;
  border-color: #bdd6ff;
  color: #1b66d6;
  background: #f4f9ff;
}

.oauth-btn:hover {
  color: #1553af;
  border-color: #a6c8ff;
  background: #eaf3ff;
}

@media (max-width: 520px) {
  .brand-block {
    padding: 20px 18px 14px;
  }

  .form-block {
    padding: 18px;
  }
}
</style>
