<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/m')
}

function tryAgain() {
  router.push('/m/survey')
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
      <h1 style="margin: 0 0 12px; font-size: 24px;">名额已满</h1>
      <p style="margin: 0 0 24px; color: #666; line-height: 1.8;">
        当前问卷填写名额已满，暂时无法继续填写。
      </p>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <van-button type="primary" block @click="goHome">
          返回首页
        </van-button>

        <van-button plain block @click="tryAgain">
          重新尝试
        </van-button>
      </div>
    </div>
  </div>
</template>