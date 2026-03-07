<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { PublicSurveyResult } from '../../api/survey'

type ReviewAnswer = Record<number, string | string[] | number>

interface SubmittedPayload {
  surveyId: number
  surveyTitle: string
  surveyDescription: string
  schema: PublicSurveyResult['schema']
  answers: ReviewAnswer
}

const route = useRoute()
const router = useRouter()

const surveyId = computed(() => {
  const id = Number(route.query.id || 1)
  return Number.isNaN(id) ? 1 : id
})

const submittedKey = computed(() => `SURVEY_SUBMITTED_${surveyId.value}`)

const loading = ref(false)
const submitted = ref<SubmittedPayload | null>(null)

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
  try {
    loading.value = true

    const raw = localStorage.getItem(submittedKey.value)
    if (!raw) {
      alert('未找到已提交记录')
      router.push('/m')
      return
    }

    submitted.value = JSON.parse(raw) as SubmittedPayload
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

function goSurvey() {
  router.push(`/m/surveys/${surveyId.value}`)
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
        当前为只读回看模式，你看到的是已提交的答案内容。
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
          {{ getAnswerText(submitted.answers[question.id]) }}
        </div>
      </div>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <van-button type="primary" block @click="goHome">
          返回首页
        </van-button>

        <van-button plain block @click="goSurvey">
          返回问卷页
        </van-button>
      </div>
    </div>
  </div>
</template>