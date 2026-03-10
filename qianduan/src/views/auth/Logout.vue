<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { beginThirdPartyLogin, shouldAutoThirdPartyForAdmin } from '../../utils/oauth-login'
import { clearLogoutContext, readLogoutContext } from '../../utils/logout-context'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()
const logoutContext = readLogoutContext()

const logoutRole = computed(() => {
  const raw = route.query.role
  if (Array.isArray(raw)) return String(raw[0] || '').trim()
  const queryRole = String(raw || '').trim()
  return queryRole || logoutContext.role
})

const logoutEntry = computed(() => {
  const raw = route.query.entry
  if (Array.isArray(raw)) {
    const queryEntry = String(raw[0] || '').trim().toLowerCase()
    return queryEntry || logoutContext.entry
  }

  const queryEntry = String(raw || '').trim().toLowerCase()
  return queryEntry || logoutContext.entry
})

const isAdminUser = computed(
  () =>
    logoutEntry.value === 'admin' ||
    logoutRole.value === 'ROLE2' ||
    logoutRole.value === 'ROLE3'
)

onMounted(() => {
  authStore.clearToken()
  clearLogoutContext()
})

function goAdminLogin() {
  if (shouldAutoThirdPartyForAdmin(settingsStore.settings)) {
    const result = beginThirdPartyLogin(settingsStore.settings, '/admin/dashboard')

    if (result.ok) {
      window.location.href = result.url
      return
    }
  }

  router.push('/local-admin/login')
}

function goPersonalLogin() {
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

      <div
        :style="{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: '12px'
        }"
      >
        <template v-if="isAdminUser">
          <el-button type="primary" @click="goAdminLogin">
            前往管理后台
          </el-button>
          <el-button @click="goPersonalLogin">
            前往个人首页
          </el-button>
        </template>

        <template v-else>
          <el-button type="primary" @click="goPersonalLogin">
            重新登录
          </el-button>
        </template>
      </div>
    </div>
  </div>
</template>
