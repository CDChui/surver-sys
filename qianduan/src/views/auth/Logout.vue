<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { beginThirdPartyLogin, shouldAutoThirdPartyForAdmin } from '../../utils/oauth-login'

const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

onMounted(() => {
  authStore.clearToken()
})

function goLogin() {
  if (shouldAutoThirdPartyForAdmin(settingsStore.settings)) {
    const result = beginThirdPartyLogin(settingsStore.settings, '/admin/dashboard')

    if (result.ok) {
      window.location.href = result.url
      return
    }
  }

  router.push('/local-admin/login')
}

function goHome() {
  router.push('/m')
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
        max-width: 520px;
        background: #fff;
        border-radius: 16px;
        padding: 36px 28px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.08);
        text-align: center;
      "
    >
      <h1 style="margin: 0 0 12px; font-size: 24px;">您已退出登录</h1>
      <p style="margin: 0 0 24px; color: #666;">当前会话已结束，请重新登录。</p>

      <div style="display: flex; justify-content: center; gap: 12px;">
        <el-button type="primary" @click="goLogin">
          前往登录
        </el-button>
        <el-button @click="goHome">
          返回首页
        </el-button>
      </div>
    </div>
  </div>
</template>
