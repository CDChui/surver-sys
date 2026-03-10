<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const surveyId = computed(() => String(route.query.id || '1'))

function goHome() {
  router.push('/m')
}

function viewSurvey() {
  router.push({
    path: '/m/review',
    query: {
      id: surveyId.value
    }
  })
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
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
      "
    >
      <div style="font-size: 48px; margin-bottom: 12px;">📝</div>
      <h1 style="margin: 0 0 12px; font-size: 24px;">你已提交过问卷</h1>
      <p style="margin: 0 0 24px; color: #666; line-height: 1.8;">
        系统检测到你已经完成过本次问卷填写，无需重复提交。
      </p>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <van-button type="primary" block @click="goHome">
          返回个人首页
        </van-button>

        <van-button plain block @click="viewSurvey">
          查看我的答案
        </van-button>
      </div>
    </div>
  </div>
</template>
