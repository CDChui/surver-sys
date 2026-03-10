<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const surveyTitle = computed(() => {
  return String(route.query.title || '问卷')
})

const surveyId = computed(() => {
  return String(route.query.id || '1')
})

const isPreviewMode = computed(() => {
  const raw = String(route.query.previewMode || '').trim().toLowerCase()
  return raw === '1' || raw === 'true' || raw === 'yes'
})

function goHome() {
  router.push('/m')
}

function viewMyAnswer() {
  if (isPreviewMode.value) {
    router.push({
      path: `/m/surveys/${surveyId.value}`,
      query: {
        previewMode: '1'
      }
    })
    return
  }

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
        box-shadow: 0 2px 8px rgba(0,0,0,0.04);
      "
    >
      <div style="font-size: 48px; margin-bottom: 12px;">✅</div>
      <h1 style="margin: 0 0 12px; font-size: 24px;">提交成功</h1>
      <p style="margin: 0 0 24px; color: #666; line-height: 1.8;">
        你已成功提交《{{ surveyTitle }}》。
      </p>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <van-button type="primary" block @click="goHome">
          返回个人首页
        </van-button>

        <van-button plain block @click="viewMyAnswer">
          查看我的答案
        </van-button>
      </div>
    </div>
  </div>
</template>
