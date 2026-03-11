<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getSurveyDetail,
  getPublicSurvey,
  submitSurvey,
  type PublicSurveyResult
} from '../../api/survey'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const submitting = ref(false)

const survey = ref<PublicSurveyResult | null>(null)
const entryToken = ref('')

const answers = reactive<Record<number, string | string[] | number>>({})

const surveyId = computed(() => {
  const id = Number(route.params.id || 1)
  return Number.isNaN(id) ? 1 : id
})

const isPreviewMode = computed(() => {
  const raw = String(route.query.previewMode || route.query.preview || '').trim().toLowerCase()
  return raw === '1' || raw === 'true' || raw === 'yes'
})

const storageUserScope = computed(() => {
  if (authStore.userId) {
    return `uid_${authStore.userId}`
  }

  const username = String(authStore.username || '')
    .trim()
    .replace(/[^a-zA-Z0-9_-]/g, '_')

  return username ? `name_${username}` : 'anon'
})

const draftKey = computed(() => {
  if (isPreviewMode.value) {
    return `SURVEY_PREVIEW_DRAFT_${storageUserScope.value}_${surveyId.value}`
  }
  return `SURVEY_DRAFT_${storageUserScope.value}_${surveyId.value}`
})

const submittedKey = computed(() => {
  return `SURVEY_SUBMITTED_${storageUserScope.value}_${surveyId.value}`
})

function goToError(type: 'not-found' | 'bad-request' | 'closed' | 'system' | 'unknown') {
  router.replace({
    path: '/m/error',
    query: {
      type
    }
  })
}

function getTextAnswer(questionId: number): string {
  const value = answers[questionId]
  return typeof value === 'string' ? value : ''
}

function setTextAnswer(questionId: number, value: string) {
  answers[questionId] = value
}

function isCheckedMulti(questionId: number, label: string) {
  const value = answers[questionId]
  return Array.isArray(value) && value.includes(label)
}

function toggleMulti(questionId: number, label: string) {
  const value = answers[questionId]

  if (!Array.isArray(value)) {
    answers[questionId] = [label]
    return
  }

  if (value.includes(label)) {
    answers[questionId] = value.filter((item) => item !== label)
  } else {
    answers[questionId] = [...value, label]
  }
}

function saveDraft() {
  localStorage.setItem(draftKey.value, JSON.stringify(answers))
}

function restoreDraft() {
  const raw = localStorage.getItem(draftKey.value)
  if (!raw) return

  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>

    Object.keys(parsed).forEach((key) => {
      const value = parsed[key]

      if (
        typeof value === 'string' ||
        typeof value === 'number' ||
        (Array.isArray(value) && value.every((item) => typeof item === 'string'))
      ) {
        answers[Number(key)] = value
      }
    })
  } catch (error) {
    console.error('草稿恢复失败', error)
  }
}

function clearDraft() {
  localStorage.removeItem(draftKey.value)
}

async function tryLoadPreviewSurveyByDetailApi() {
  if (!isPreviewMode.value) return false

  try {
    const response = await getSurveyDetail(surveyId.value)
    if (response.code !== 20000 || !response.data) {
      return false
    }

    survey.value = {
      id: response.data.id,
      title: response.data.title,
      description: response.data.description,
      schema: response.data.schema,
      entryToken: ''
    }
    entryToken.value = ''
    restoreDraft()
    return true
  } catch (error) {
    return false
  }
}

function saveSubmittedRecord(submitTime: string) {
  if (isPreviewMode.value) return
  if (!survey.value) return

  const payload = {
    surveyId: surveyId.value,
    surveyTitle: survey.value.title,
    surveyDescription: survey.value.description,
    schema: survey.value.schema,
    answers: JSON.parse(JSON.stringify(answers)),
    userId: authStore.userId,
    username: authStore.username,
    submitTime
  }

  localStorage.setItem(submittedKey.value, JSON.stringify(payload))
}

function validateAnswers() {
  if (!survey.value) return false

  for (const question of survey.value.schema) {
    if (!question.required) continue

    const value = answers[question.id]

    if (question.type === 'single' && !value) {
      alert(`请完成题目：${question.title}`)
      return false
    }

    if (question.type === 'multi' && (!Array.isArray(value) || value.length === 0)) {
      alert(`请完成题目：${question.title}`)
      return false
    }

    if ((question.type === 'text' || question.type === 'textarea') && !String(value || '').trim()) {
      alert(`请完成题目：${question.title}`)
      return false
    }

    if (question.type === 'rate' && (value === undefined || value === null || value === '')) {
      alert(`请完成题目：${question.title}`)
      return false
    }
  }

  return true
}

async function handleSubmit() {
  if (!validateAnswers()) return

  try {
    submitting.value = true

    const response = await submitSurvey({
      surveyId: surveyId.value,
      answers: JSON.parse(JSON.stringify(answers)),
      entryToken: entryToken.value || undefined,
      previewMode: isPreviewMode.value
    })

    if (isPreviewMode.value) {
      if (response.code === 20000 || response.code === 40009) {
        clearDraft()
        alert('模拟提交，问卷答案不收录')
        await router.replace('/admin/surveys')
        return
      }
    }

    if (response.code !== 20000) {
      alert(response.message || '提交失败')
      return
    }

    saveSubmittedRecord(response.data.submitTime)
    clearDraft()

    router.push({
      path: '/m/success',
      query: {
        id: String(surveyId.value),
        title: survey.value?.title || '问卷'
      }
    })
  } catch (error) {
    goToError('system')
  } finally {
    submitting.value = false
  }
}

async function loadSurvey() {
  try {
    loading.value = true

    if (!route.params.id || Number.isNaN(Number(route.params.id))) {
      goToError('bad-request')
      return
    }

    const modeQuery = String(route.query.mode || 'normal') as
      | 'normal'
      | 'quota'
      | 'duplicate'

    const response = await getPublicSurvey(surveyId.value, {
      mode: modeQuery,
      previewMode: isPreviewMode.value
    })

    if (response.code === 40011) {
      router.replace('/m/blocked/quota')
      return
    }

    if (response.code === 40404) {
      goToError('not-found')
      return
    }

    if (response.code === 40009) {
      if (isPreviewMode.value) {
        const loadedByFallback = await tryLoadPreviewSurveyByDetailApi()
        if (loadedByFallback) {
          return
        }
      } else {
        router.replace({
          path: '/m/blocked/duplicate',
          query: {
            id: String(surveyId.value)
          }
        })
        return
      }
    }

    if (response.code === 40001) {
      goToError('bad-request')
      return
    }

    if (response.code !== 20000 || !response.data) {
      goToError('not-found')
      return
    }

    survey.value = response.data
    entryToken.value = response.data.entryToken || ''
    restoreDraft()
  } catch (error) {
    goToError('system')
  } finally {
    loading.value = false
  }
}

function getRateText(value: number) {
  return `${value} 分`
}

function getRateScores(question: PublicSurveyResult['schema'][number]) {
  const rawMin = typeof question.min === 'number' ? question.min : 1
  const rawMax = typeof question.max === 'number' ? question.max : 5
  const min = Number.isFinite(rawMin) ? Math.floor(rawMin) : 1
  const max = Number.isFinite(rawMax) ? Math.floor(rawMax) : min + 4
  const safeMax = Math.max(max, min)
  const scores: number[] = []
  for (let score = min; score <= safeMax; score += 1) {
    scores.push(score)
  }
  return scores
}

watch(
  answers,
  () => {
    if (!survey.value) return
    saveDraft()
  },
  { deep: true }
)

onMounted(() => {
  void loadSurvey()
})
</script>

<template>
  <div style="min-height: 100vh; background: #f7f8fa; padding: 16px;">
    <div v-if="loading" style="text-align: center; padding: 60px 0;">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <div v-else-if="survey" style="max-width: 760px; margin: 0 auto;">
      <div
        style="
          background: #fff;
          border-radius: 12px;
          padding: 20px;
          margin-bottom: 16px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.04);
        "
      >
        <h1 style="margin: 0 0 12px; font-size: 22px;">{{ survey.title }}</h1>
        <p style="margin: 0; color: #666; line-height: 1.7;">
          {{ survey.description }}
        </p>
      </div>

      <div
        style="
          background: #fff7e6;
          border: 1px solid #ffe7ba;
          color: #ad6800;
          border-radius: 12px;
          padding: 14px 16px;
          margin-bottom: 16px;
        "
      >
        当前页面已开启断点续答，填写内容会自动保存。
      </div>

      <div
        v-for="(question, index) in survey.schema"
        :key="question.id"
        style="
          background: #fff;
          border-radius: 12px;
          padding: 20px;
          margin-bottom: 16px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.04);
        "
      >
        <div style="margin-bottom: 14px; font-weight: 700; line-height: 1.7;">
          {{ index + 1 }}. {{ question.title }}
          <span v-if="question.required" style="color: #f56c6c;"> *</span>
        </div>

        <div v-if="question.type === 'single'">
          <van-radio-group v-model="answers[question.id]">
            <div
              v-for="option in question.options"
              :key="option.id"
              style="margin-bottom: 10px;"
            >
              <van-radio :name="option.label">{{ option.label }}</van-radio>
            </div>
          </van-radio-group>
        </div>

        <div v-else-if="question.type === 'multi'">
          <div
            v-for="option in question.options"
            :key="option.id"
            style="
              margin-bottom: 10px;
              padding: 12px;
              border: 1px solid #e5e6eb;
              border-radius: 8px;
              cursor: pointer;
            "
            @click="toggleMulti(question.id, option.label)"
          >
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <span>{{ option.label }}</span>
              <span v-if="isCheckedMulti(question.id, option.label)">✓</span>
            </div>
          </div>
        </div>

        <div v-else-if="question.type === 'text'">
          <van-field
            :model-value="getTextAnswer(question.id)"
            placeholder="请输入内容"
            input-align="left"
            @update:model-value="setTextAnswer(question.id, $event)"
          />
        </div>

        <div v-else-if="question.type === 'textarea'">
          <van-field
            :model-value="getTextAnswer(question.id)"
            rows="4"
            autosize
            type="textarea"
            placeholder="请输入内容"
            @update:model-value="setTextAnswer(question.id, $event)"
          />
        </div>

        <div v-else-if="question.type === 'rate'">
          <div style="display: flex; gap: 10px; flex-wrap: wrap;">
            <van-button
              v-for="score in getRateScores(question)"
              :key="score"
              size="small"
              :type="answers[question.id] === score ? 'primary' : 'default'"
              @click="answers[question.id] = score"
            >
              {{ getRateText(score) }}
            </van-button>
          </div>
        </div>
      </div>

      <van-button
        type="primary"
        block
        :loading="submitting"
        @click="handleSubmit"
      >
        提交问卷
      </van-button>
    </div>
  </div>
</template>


