<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getMySurveySubmissionDetail,
  type MySurveySubmissionDetailResult
} from '../../api/survey'

const route = useRoute()
const router = useRouter()

const surveyId = computed(() => {
  const id = Number(route.query.id || 1)
  return Number.isNaN(id) ? 0 : id
})

const loading = ref(false)
const submitted = ref<MySurveySubmissionDetailResult | null>(null)

function getAnswerText(value: string | string[] | number | undefined) {
  if (Array.isArray(value)) {
    return value.join('、') || '未填写'
  }

  if (value === undefined || value === '' || value === null) {
    return '未填写'
  }

  return String(value)
}

async function loadSubmitted() {
  if (!surveyId.value) {
    alert('问卷参数错误')
    router.push('/m')
    return
  }

  try {
    loading.value = true
    const response = await getMySurveySubmissionDetail(surveyId.value)

    if (response.code !== 20000) {
      alert(response.message || '未找到已提交记录')
      router.push('/m')
      return
    }

    submitted.value = response.data
  } catch (error) {
    alert('加载已提交答案失败')
    router.push('/m')
  } finally {
    loading.value = false
  }
}

function goHome() {
  router.push('/m')
}

onMounted(() => {
  void loadSubmitted()
})
</script>

<template>
  <div style="min-height: 100vh; background: #f7f8fa; padding: 16px;">
    <div v-if="loading" style="text-align: center; padding: 60px 0;">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <div v-else-if="submitted" style="max-width: 760px; margin: 0 auto;">
      <div
        style="
          background: #fff;
          border-radius: 12px;
          padding: 20px;
          margin-bottom: 16px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.04);
        "
      >
        <h1 style="margin: 0 0 12px; font-size: 22px;">
          {{ submitted.surveyTitle }}
        </h1>
        <p style="margin: 0; color: #666; line-height: 1.7;">
          {{ submitted.surveyDescription }}
        </p>
        <div style="margin-top: 12px; color: #666; font-size: 13px;">
          提交时间：{{ submitted.submitTime || '-' }}
        </div>
      </div>

      <div
        style="
          background: #e6f4ff;
          border: 1px solid #91caff;
          color: #0958d9;
          border-radius: 12px;
          padding: 14px 16px;
          margin-bottom: 16px;
        "
      >
        当前为只读回看模式，你看到的是已提交时保存的答案与问卷快照。
      </div>

      <div
        v-for="(question, index) in submitted.schema"
        :key="question.id"
        style="
          background: #fff;
          border-radius: 12px;
          padding: 20px;
          margin-bottom: 16px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.04);
        "
      >
        <div style="margin-bottom: 12px; font-weight: 700; line-height: 1.7;">
          {{ index + 1 }}. {{ question.title }}
        </div>

        <div
          style="
            background: #fafafa;
            border: 1px solid #eee;
            border-radius: 8px;
            padding: 12px 14px;
            color: #333;
            line-height: 1.8;
          "
        >
          {{ getAnswerText(submitted.answers[String(question.id)]) }}
        </div>
      </div>

      <van-button type="primary" block @click="goHome">
        返回个人首页
      </van-button>
    </div>
  </div>
</template>

