<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { appendOperationLog } from '../../utils/log'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

const pageTitle = computed(() => {
  const path = route.path

  if (path.includes('/admin/dashboard')) return '数据看板'
  if (path.includes('/admin/surveys/new')) return '创建问卷'
  if (path.includes('/admin/surveys/') && path.includes('/edit')) return '编辑问卷'
  if (path.includes('/admin/surveys/') && path.includes('/stats')) return '问卷统计'
  if (path.includes('/admin/users')) return '用户管理'
  if (path.includes('/admin/permissions')) return '授权管理'
  if (path.includes('/admin/logs')) return '日志中心'
  if (path.includes('/admin/settings')) return '系统设置'
  return '问卷管理'
})

function goDashboard() {
  router.push('/admin/dashboard')
}

function goSurveys() {
  router.push('/admin/surveys')
}

function goUsers() {
  router.push('/admin/users')
}

function goPermissions() {
  router.push('/admin/permissions')
}

function goLogs() {
  router.push('/admin/logs')
}

function goSettings() {
  router.push('/admin/settings')
}

function handleLogout() {
  appendOperationLog({
    module: 'SYSTEM',
    action: 'LOGOUT',
    target: '后台退出登录'
  })
  authStore.clearToken()
  router.push('/auth/logout')
}
</script>

<template>
  <div style="min-height: 100vh; background: #f5f7fa; display: flex;">
    <aside
      style="
        width: 220px;
        background: #001529;
        color: #fff;
        padding: 20px 0;
        box-sizing: border-box;
      "
    >
      <div
        style="
          padding: 0 20px 20px;
          font-size: 20px;
          font-weight: 700;
          border-bottom: 1px solid rgba(255,255,255,0.08);
          margin-bottom: 16px;
        "
      >
        {{ settingsStore.settings.systemName }}
      </div>

      <div style="padding: 0 12px;">
        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/dashboard') ? '#1677ff' : 'transparent'
          }"
          @click="goDashboard"
        >
          数据看板
        </div>

        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/surveys') ? '#1677ff' : 'transparent'
          }"
          @click="goSurveys"
        >
          问卷管理
        </div>

        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/users') ? '#1677ff' : 'transparent'
          }"
          @click="goUsers"
        >
          用户管理
        </div>

        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/permissions') ? '#1677ff' : 'transparent'
          }"
          @click="goPermissions"
        >
          授权管理
        </div>

        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/logs') ? '#1677ff' : 'transparent'
          }"
          @click="goLogs"
        >
          日志中心
        </div>

        <div
          :style="{
            padding: '12px 14px',
            borderRadius: '8px',
            marginBottom: '8px',
            cursor: 'pointer',
            background: route.path.startsWith('/admin/settings') ? '#1677ff' : 'transparent'
          }"
          @click="goSettings"
        >
          系统设置
        </div>
      </div>
    </aside>

    <div style="flex: 1; min-width: 0;">
      <header
        style="
          height: 64px;
          background: #fff;
          border-bottom: 1px solid #eee;
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 24px;
          box-sizing: border-box;
        "
      >
        <div style="font-size: 18px; font-weight: 700;">
          {{ pageTitle }}
        </div>

        <div style="display: flex; align-items: center; gap: 12px;">
          <span style="color: #666;">管理员</span>
          <el-button type="danger" plain size="small" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </header>

      <main style="padding: 24px; box-sizing: border-box;">
        <router-view />
      </main>
    </div>
  </div>
</template>
