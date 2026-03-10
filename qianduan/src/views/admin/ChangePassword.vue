<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { changeOwnPasswordApi } from '../../api/user'
import { appendOperationLog } from '../../utils/log'

const authStore = useAuthStore()
const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

async function handleSubmit() {
  if (!authStore.localAccount) {
    alert('第三方账号不支持本地密码修改')
    return
  }

  const oldPassword = form.oldPassword.trim()
  const newPassword = form.newPassword.trim()
  const confirmPassword = form.confirmPassword.trim()

  if (!oldPassword) {
    alert('请输入旧密码')
    return
  }
  if (!newPassword) {
    alert('请输入新密码')
    return
  }
  if (newPassword.length < 6 || newPassword.length > 64) {
    alert('新密码长度需为 6-64 位')
    return
  }
  if (newPassword !== confirmPassword) {
    alert('两次输入的新密码不一致')
    return
  }
  if (oldPassword === newPassword) {
    alert('新密码不能与旧密码一致')
    return
  }

  try {
    submitting.value = true
    const response = await changeOwnPasswordApi(oldPassword, newPassword)

    if (response.code !== 20000) {
      alert(response.message || '修改密码失败')
      return
    }

    appendOperationLog({
      module: 'USER',
      action: 'UPDATE',
      target: `CHANGE_OWN_PASSWORD:${authStore.username}`
    })

    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    alert('密码修改成功')
  } catch (error) {
    const message =
      error instanceof Error && error.message ? error.message : '修改密码失败'
    alert(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card style="max-width: 620px;">
    <template #header>
      <div style="font-weight: 700;">修改密码</div>
    </template>

    <div v-if="!authStore.localAccount" style="color: #666;">
      当前账号为第三方登录账号，不支持本地密码修改。
    </div>

    <div v-else style="display: grid; gap: 14px;">
      <div>
        <div style="margin-bottom: 6px; font-weight: 600;">旧密码</div>
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="请输入旧密码"
        />
      </div>

      <div>
        <div style="margin-bottom: 6px; font-weight: 600;">新密码</div>
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="请输入新密码（6-64位）"
        />
      </div>

      <div>
        <div style="margin-bottom: 6px; font-weight: 600;">确认新密码</div>
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="请再次输入新密码"
        />
      </div>

      <div>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          提交修改
        </el-button>
      </div>
    </div>
  </el-card>
</template>
