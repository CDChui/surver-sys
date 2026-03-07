<script setup lang="ts">
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()

function logout() {
  authStore.clearToken()
  alert('已退出登录')
  location.href = '/local-admin/login'
}
</script>

<template>
  <div style="padding: 20px;">
    <h1>后台首页</h1>
    <p>你已经登录成功。</p>
    <p>当前 token：{{ authStore.token || '无' }}</p>

    <el-button type="danger" @click="logout">
      退出登录
    </el-button>
  </div>
</template>