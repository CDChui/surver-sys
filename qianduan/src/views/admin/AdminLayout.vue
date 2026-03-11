<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { logoutApi } from '../../api/auth'
import { useSettingsStore } from '../../stores/settings'
import { appendOperationLog } from '../../utils/log'
import { setLogoutContext } from '../../utils/logout-context'
import {
  DataAnalysis,
  Document,
  Key,
  Setting,
  Tickets,
  User
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()
const adminLogo = computed(() => settingsStore.settings.adminLogo || '')

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
  if (path.includes('/admin/password')) return '修改密码'
  return '问卷管理'
})

function isActive(prefix: string) {
  return route.path.startsWith(prefix)
}

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
  router.push('/admin/logs/system')
}

function goSettings() {
  router.push('/admin/settings')
}

function goChangePassword() {
  router.push('/admin/password')
}

async function handleLogout() {
  const currentRole = authStore.role
  setLogoutContext('admin', currentRole)

  try {
    await logoutApi()
  } catch (error) {
    // keep local logout flow even when server logout fails
  }

  appendOperationLog({
    module: 'SYSTEM',
    action: 'LOGOUT',
    target: '后台退出登录'
  })
  authStore.clearToken()
  router.push({
    path: '/auth/logout',
    query: {
      role: currentRole,
      entry: 'admin'
    }
  })
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="sidebar-brand">
        <div class="sidebar-logo">
          <img
            v-if="adminLogo"
            :src="adminLogo"
            alt="管理后台 Logo"
            class="sidebar-logo-image"
          >
          <span v-else class="sidebar-logo-fallback">LOGO</span>
        </div>

        <div class="sidebar-title">
          {{ settingsStore.settings.systemName }}
        </div>
      </div>

      <div class="sidebar-menu">
        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/dashboard') }"
          @click="goDashboard"
        >
          <el-icon class="sidebar-item-icon"><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </div>

        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/surveys') }"
          @click="goSurveys"
        >
          <el-icon class="sidebar-item-icon"><Document /></el-icon>
          <span>问卷管理</span>
        </div>

        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/users') }"
          @click="goUsers"
        >
          <el-icon class="sidebar-item-icon"><User /></el-icon>
          <span>用户管理</span>
        </div>

        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/permissions') }"
          @click="goPermissions"
        >
          <el-icon class="sidebar-item-icon"><Key /></el-icon>
          <span>授权管理</span>
        </div>

        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/logs') }"
          @click="goLogs"
        >
          <el-icon class="sidebar-item-icon"><Tickets /></el-icon>
          <span>日志中心</span>
        </div>

        <div
          class="sidebar-item"
          :class="{ active: isActive('/admin/settings') }"
          @click="goSettings"
        >
          <el-icon class="sidebar-item-icon"><Setting /></el-icon>
          <span>系统设置</span>
        </div>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-header">
        <div class="admin-header-title">
          {{ pageTitle }}
        </div>

        <div class="admin-header-actions">
          <span class="admin-user-name">
            {{ authStore.realName || authStore.username || '管理员' }}
          </span>

          <el-button
            v-if="authStore.localAccount"
            plain
            size="small"
            @click="goChangePassword"
          >
            修改密码
          </el-button>

          <el-button type="danger" plain size="small" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  --sidebar-width: 228px;
  min-height: 100vh;
  background: #f5f7fa;
}

.admin-sidebar {
  width: var(--sidebar-width);
  background: linear-gradient(180deg, #001529 0%, #0a2342 100%);
  color: #fff;
  padding: 18px 0;
  box-sizing: border-box;
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  overflow-y: auto;
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.06);
}

.sidebar-brand {
  padding: 0 18px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 14px;
}

.sidebar-logo {
  height: 44px;
  border-radius: 10px;
  background: #ffffff;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid #dbe3ef;
}

.sidebar-logo-image {
  max-width: 100%;
  max-height: 36px;
  object-fit: contain;
}

.sidebar-logo-fallback {
  font-size: 12px;
  color: #8b95a3;
  letter-spacing: 0.5px;
}

.sidebar-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sidebar-menu {
  padding: 0 12px;
  display: grid;
  gap: 6px;
}

.sidebar-item {
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.92);
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.12);
}

.sidebar-item.active {
  background: linear-gradient(135deg, #2f80ff 0%, #1677ff 100%);
  box-shadow: 0 6px 12px rgba(22, 119, 255, 0.25);
}

.sidebar-item-icon {
  font-size: 16px;
  opacity: 0.9;
}

.sidebar-item.active .sidebar-item-icon {
  opacity: 1;
}

.admin-main {
  margin-left: var(--sidebar-width);
  min-width: 0;
}

.admin-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-sizing: border-box;
}

.admin-header-title {
  font-size: 18px;
  font-weight: 700;
}

.admin-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-user-name {
  color: #666;
}

.admin-content {
  padding: 24px;
  box-sizing: border-box;
}
</style>
