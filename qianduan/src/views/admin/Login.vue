<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore, type UserRole } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { appendOperationLog } from '../../utils/log'
import { getPostLoginPath } from '../../utils/auth-redirect'
import {
  beginThirdPartyLogin,
  getDefaultEnabledProvider,
  shouldAutoThirdPartyForAdmin
} from '../../utils/oauth-login'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

const username = ref('admin')
const password = ref('123456')
const role = ref<UserRole>('ROLE3')
const loading = ref(false)
const oauthLoading = ref(false)

const canUseThirdPartyLogin = computed(() =>
  shouldAutoThirdPartyForAdmin(settingsStore.settings)
)

const defaultProviderName = computed(() => {
  const provider = getDefaultEnabledProvider(settingsStore.settings)
  return provider?.name || '第三方认证'
})

function getMockUserInfo(selectedRole: UserRole) {
  if (selectedRole === 'ROLE1') {
    return {
      token: 'mock-token-role1',
      role: 'ROLE1' as UserRole,
      username: 'student01',
      realName: '王同学',
      userId: 4
    }
  }

  if (selectedRole === 'ROLE2') {
    return {
      token: 'mock-token-role2',
      role: 'ROLE2' as UserRole,
      username: 'teacher01',
      realName: '张老师',
      userId: 2
    }
  }

  return {
    token: 'mock-token-role3',
    role: 'ROLE3' as UserRole,
    username: 'admin',
    realName: '系统管理员',
    userId: 1
  }
}

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

    await new Promise((resolve) => setTimeout(resolve, 400))

    const userInfo = getMockUserInfo(role.value)
    authStore.setAuth(userInfo)
    appendOperationLog({
      module: 'SYSTEM',
      action: 'LOGIN',
      target: `后台登录（${userInfo.role}）`
    })

    alert('登录成功')
    router.push(getPostLoginPath(userInfo.role, route.query.redirect))
  } catch (error) {
    alert('登录失败')
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
        max-width: 420px;
        background: #fff;
        border-radius: 16px;
        padding: 32px 24px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.08);
      "
    >
      <h1 style="margin: 0 0 24px; text-align: center;">后台登录</h1>

      <div style="margin-bottom: 16px;">
        <div style="margin-bottom: 8px; font-weight: 600;">用户名</div>
        <el-input v-model="username" placeholder="请输入用户名" />
      </div>

      <div style="margin-bottom: 16px;">
        <div style="margin-bottom: 8px; font-weight: 600;">密码</div>
        <el-input
          v-model="password"
          type="password"
          show-password
          placeholder="请输入密码"
        />
      </div>

      <div style="margin-bottom: 24px;">
        <div style="margin-bottom: 8px; font-weight: 600;">模拟角色</div>
        <el-select v-model="role" style="width: 100%;">
          <el-option label="普通用户 ROLE1" value="ROLE1" />
          <el-option label="业务管理员 ROLE2" value="ROLE2" />
          <el-option label="系统管理员 ROLE3" value="ROLE3" />
        </el-select>
      </div>

      <el-button
        type="primary"
        :loading="loading"
        style="width: 100%;"
        @click="handleLogin"
      >
        登录
      </el-button>

      <div style="margin-top: 14px; text-align: center;">
        <el-button
          link
          type="primary"
          :loading="oauthLoading"
          :disabled="!canUseThirdPartyLogin || loading"
          @click="handleThirdPartyLogin"
        >
          使用{{ defaultProviderName }}登录
        </el-button>
      </div>
    </div>
  </div>
</template>
