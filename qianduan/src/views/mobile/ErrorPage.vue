<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const errorType = computed(() => String(route.query.type || 'unknown'))

const errorTitle = computed(() => {
  if (errorType.value === 'not-found') return '问卷不存在'
  if (errorType.value === 'bad-request') return '请求参数错误'
  if (errorType.value === 'closed') return '问卷已关闭'
  if (errorType.value === 'system') return '系统开小差了'
  return '页面出现异常'
})

const errorDescription = computed(() => {
  if (errorType.value === 'not-found') {
    return '你访问的问卷不存在，或已被删除。'
  }

  if (errorType.value === 'bad-request') {
    return '当前访问参数不正确，请检查链接是否完整。'
  }

  if (errorType.value === 'closed') {
    return '当前问卷已关闭，暂时无法继续填写。'
  }

  if (errorType.value === 'system') {
    return '系统处理请求时出现异常，请稍后再试。'
  }

  return '当前页面无法正常打开，请返回首页后重试。'
})

function goHome() {
  router.push('/m')
}

function goBack() {
  router.back()
}
</script>

<template>
  <div
    style="
      min-height: 100vh;
      background: #f7f8fa;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    "
  >
    <div
      style="
        width: 100%;
        max-width: 520px;
        background: #fff;
        border-radius: 16px;
        padding: 32px 24px;
        text-align: center;
        box-shadow: 0 2px 8px rgba(0,0,0,0.04);
      "
    >
      <div style="font-size: 48px; margin-bottom: 12px;">⚠️</div>

      <h1 style="margin: 0 0 12px; font-size: 24px;">
        {{ errorTitle }}
      </h1>

      <p style="margin: 0 0 24px; color: #666; line-height: 1.8;">
        {{ errorDescription }}
      </p>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <van-button type="primary" block @click="goHome">
          返回首页
        </van-button>

        <van-button plain block @click="goBack">
          返回上一页
        </van-button>
      </div>
    </div>
  </div>
</template>