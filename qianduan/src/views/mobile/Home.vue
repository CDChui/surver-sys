<script setup lang="ts">
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()

function saveToken() {
  authStore.setAuth({
    token: 'demo-token-123',
    role: 'ROLE1',
    username: 'demo-user',
    realName: 'Demo User',
    userId: 1
  })
  alert('token 已保存到 localStorage')
}

function clearToken() {
  authStore.clearToken()
  alert('token 已清除')
}
</script>

<template>
  <div style="padding: 20px;">
    <h1>问卷系统</h1>
    <p>移动端首页</p>

    <van-button type="primary" block style="margin-bottom: 12px;">
      开始填写问卷
    </van-button>

    <van-button type="success" block style="margin-bottom: 12px;" @click="saveToken">
      测试保存 Token
    </van-button>

    <van-button type="danger" block @click="clearToken">
      测试清除 Token
    </van-button>
  </div>
</template>
