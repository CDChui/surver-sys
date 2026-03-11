<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getSurveyDetail,
  updateSurvey,
  type QuestionOption,
  type QuestionSchemaItem,
  type QuestionType
} from '../../api/survey'
import { useSurveyStore } from '../../stores/survey'
import { appendOperationLog } from '../../utils/log'

const route = useRoute()
const router = useRouter()
const surveyStore = useSurveyStore()

const loading = ref(false)
const pageLoading = ref(false)

const surveyId = computed(() => Number(route.params.id))

const form = reactive({
  title: '',
  description: '',
  allowDuplicateSubmit: false,
  questions: [] as QuestionSchemaItem[]
})

async function loadSurveyDetail() {
  try {
    pageLoading.value = true

    const response = await getSurveyDetail(surveyId.value)
    if (response.code !== 20000 || !response.data) {
      alert(response.message || '加载问卷详情失败')
      return
    }
    const result = response.data

    form.title = result.title
    form.description = result.description
    form.allowDuplicateSubmit = Boolean(result.allowDuplicateSubmit)
    form.questions = result.schema.map((item) => {
      const question: QuestionSchemaItem = {
        id: item.id,
        type: item.type,
        title: item.title,
        required: item.required,
        options: (item.options || []).map((opt) => ({
          id: opt.id,
          label: opt.label
        }))
      }

      if (item.type === 'rate') {
        question.min = typeof item.min === 'number' ? item.min : 1
        question.max = typeof item.max === 'number' ? item.max : 5
      }

      return question
    })
  } catch (error) {
    alert('加载问卷详情失败')
  } finally {
    pageLoading.value = false
  }
}

async function handleSaveEdit() {
  if (!form.title) {
    alert('请输入问卷标题')
    return
  }

  try {
    loading.value = true

    const response = await updateSurvey({
      id: surveyId.value,
      title: form.title,
      description: form.description,
      questions: form.questions,
      allowDuplicateSubmit: form.allowDuplicateSubmit
    })

    if (response.code !== 20000 || !response.data) {
      alert(response.message || '保存修改失败')
      return
    }

    const result = response.data

    surveyStore.updateSurvey({
      id: result.id,
      title: result.title,
      description: result.description,
      schema: result.schema,
      allowDuplicateSubmit: Boolean(result.allowDuplicateSubmit)
    })

    appendOperationLog({
      module: 'SURVEY',
      action: 'UPDATE',
      target: result.title
    })

    alert(`修改成功，问卷ID：${result.id}，题目数：${result.schema.length}`)
    router.push('/admin/surveys')
  } catch (error) {
    alert('保存修改失败')
  } finally {
    loading.value = false
  }
}

function addQuestion(type: QuestionType) {
  const typeTextMap: Record<QuestionType, string> = {
    single: '单选题',
    multi: '多选题',
    text: '填空题',
    textarea: '简答题',
    rate: '评分题'
  }

  const newQuestion: QuestionSchemaItem = {
    id: Date.now() + Math.floor(Math.random() * 1000),
    type,
    title: `请填写${typeTextMap[type]}标题`,
    required: false,
    options:
      type === 'single' || type === 'multi'
        ? [
            { id: Date.now() + 1, label: '选项1' },
            { id: Date.now() + 2, label: '选项2' }
          ]
        : []
  }

  if (type === 'rate') {
    newQuestion.min = 1
    newQuestion.max = 5
  }

  form.questions.push(newQuestion)
}

function removeQuestion(id: number) {
  const index = form.questions.findIndex((item) => item.id === id)
  if (index !== -1) {
    form.questions.splice(index, 1)
  }
}

function moveQuestionUp(index: number) {
  if (index <= 0) return

  form.questions.splice(
    index - 1,
    2,
    form.questions[index]!,
    form.questions[index - 1]!
  )
}

function moveQuestionDown(index: number) {
  if (index >= form.questions.length - 1) return

  form.questions.splice(
    index,
    2,
    form.questions[index + 1]!,
    form.questions[index]!
  )
}

function isChoiceQuestion(question: QuestionSchemaItem) {
  return question.type === 'single' || question.type === 'multi'
}

function isRateQuestion(question: QuestionSchemaItem) {
  return question.type === 'rate'
}

function clampInt(value: unknown, min: number, max: number) {
  const num = Number(value)
  if (!Number.isFinite(num)) return min
  const intVal = Math.floor(num)
  return Math.min(Math.max(intVal, min), max)
}

function normalizeRateRange(question: QuestionSchemaItem) {
  if (question.type !== 'rate') return
  const min = Number.isFinite(question.min) ? Number(question.min) : 1
  const max = Number.isFinite(question.max) ? Number(question.max) : min + 4
  const safeMin = Math.floor(min)
  const safeMax = Math.max(Math.floor(max), safeMin)
  question.min = safeMin
  question.max = safeMax
}

function getRateMin(question: QuestionSchemaItem) {
  normalizeRateRange(question)
  return question.min ?? 1
}

function getRateMax(question: QuestionSchemaItem) {
  normalizeRateRange(question)
  return question.max ?? getRateMin(question)
}

function getRateCount(question: QuestionSchemaItem) {
  const min = getRateMin(question)
  const max = getRateMax(question)
  return Math.max(1, max - min + 1)
}

function updateRateMin(question: QuestionSchemaItem, value: number | undefined) {
  const count = getRateCount(question)
  const min = clampInt(value, 0, 100)
  question.min = min
  question.max = min + count - 1
}

function updateRateCount(question: QuestionSchemaItem, value: number | undefined) {
  const count = clampInt(value, 1, 10)
  const min = getRateMin(question)
  question.max = min + count - 1
}

function getQuestionOptions(question: QuestionSchemaItem): QuestionOption[] {
  return question.options || []
}

function addOption(question: QuestionSchemaItem) {
  question.options.push({
    id: Date.now() + Math.floor(Math.random() * 1000),
    label: `选项${question.options.length + 1}`
  })
}

function removeOption(question: QuestionSchemaItem, optionId: number) {
  const index = question.options.findIndex((item) => item.id === optionId)
  if (index !== -1) {
    question.options.splice(index, 1)
  }
}

function getQuestionTypeText(type: QuestionType) {
  const map: Record<QuestionType, string> = {
    single: '单选题',
    multi: '多选题',
    text: '填空题',
    textarea: '简答题',
    rate: '评分题'
  }

  return map[type]
}

function handleBack() {
  router.push('/admin/surveys')
}

onMounted(() => {
  loadSurveyDetail()
})
</script>

<template>
  <div style="padding: 24px; min-height: 100vh; background: #f5f7fa;">
    <el-card v-loading="pageLoading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <h2 style="margin: 0;">编辑问卷</h2>
            <p style="margin: 8px 0 0; color: #666;">
              当前问卷ID：{{ surveyId }}
            </p>
          </div>          <div style="display: flex; align-items: center; gap: 16px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-switch v-model="form.allowDuplicateSubmit" />
              <span style="font-weight: 600; color: #333;">允许重复提交</span>
            </div>

            <el-button @click="handleBack">
              返回列表
            </el-button>
          </div>
        </div>
      </template>

      <div style="max-width: 1000px;">
        <div style="margin-bottom: 20px;">
          <div style="margin-bottom: 8px; font-weight: 600;">问卷标题</div>
          <el-input v-model="form.title" placeholder="请输入问卷标题" />
        </div>

        <div style="margin-bottom: 20px;">
          <div style="margin-bottom: 8px; font-weight: 600;">问卷描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入问卷描述"
          />
        </div>

        <div
          style="margin: 24px 0; padding: 16px; background: #fafafa; border: 1px solid #eee; border-radius: 8px;"
        >
          <div style="margin-bottom: 12px; font-weight: 600;">添加题目</div>

          <div style="display: flex; flex-wrap: wrap; gap: 12px;">
            <el-button @click="addQuestion('single')">添加单选题</el-button>
            <el-button @click="addQuestion('multi')">添加多选题</el-button>
            <el-button @click="addQuestion('text')">添加填空题</el-button>
            <el-button @click="addQuestion('textarea')">添加简答题</el-button>
            <el-button @click="addQuestion('rate')">添加评分题</el-button>
          </div>
        </div>

        <div style="margin-bottom: 24px;">
          <div style="margin-bottom: 12px; font-weight: 600;">题目列表</div>

          <div v-if="form.questions.length === 0" style="color: #999;">
            当前还没有题目，请点击上方按钮添加
          </div>

          <el-card
            v-for="(question, index) in form.questions"
            :key="question.id"
            style="margin-bottom: 12px;"
          >
            <div
              style="margin-bottom: 8px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; gap: 12px;"
            >
              <span>第 {{ index + 1 }} 题 - {{ getQuestionTypeText(question.type) }}</span>

              <div style="display: flex; gap: 8px;">
                <el-button
                  size="small"
                  :disabled="index === 0"
                  @click="moveQuestionUp(index)"
                >
                  上移
                </el-button>

                <el-button
                  size="small"
                  :disabled="index === form.questions.length - 1"
                  @click="moveQuestionDown(index)"
                >
                  下移
                </el-button>

                <el-button type="danger" link @click="removeQuestion(question.id)">
                  删除
                </el-button>
              </div>
            </div>

            <el-input
              v-model="question.title"
              placeholder="请输入题目标题"
            />

            <div style="margin-top: 12px;">
              <el-switch v-model="question.required" />
              <span style="margin-left: 8px; color: #666;">
                {{ question.required ? '必填' : '非必填' }}
              </span>
            </div>

            <div
              v-if="isChoiceQuestion(question)"
              style="margin-top: 12px;"
            >
              <div
                style="margin-bottom: 8px; color: #666; display: flex; justify-content: space-between; align-items: center;"
              >
                <span>选项列表</span>
                <el-button type="primary" link @click="addOption(question)">
                  新增选项
                </el-button>
              </div>

              <div
                v-for="(option, optionIndex) in getQuestionOptions(question)"
                :key="option.id"
                style="display: flex; gap: 8px; margin-bottom: 8px;"
              >
                <el-input
                  v-model="option.label"
                  :placeholder="`请输入选项${optionIndex + 1}`"
                />
                <el-button
                  type="danger"
                  plain
                  @click="removeOption(question, option.id)"
                >
                  删除
                </el-button>
              </div>
            </div>

            <div
              v-if="isRateQuestion(question)"
              style="margin-top: 12px;"
            >
              <div style="margin-bottom: 8px; color: #666;">评分设置</div>
              <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center;">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="color: #666;">起始分值</span>
                  <el-input-number
                    :model-value="getRateMin(question)"
                    :min="0"
                    :max="100"
                    @update:model-value="(value) => updateRateMin(question, value)"
                  />
                </div>

                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="color: #666;">选项数量</span>
                  <el-input-number
                    :model-value="getRateCount(question)"
                    :min="1"
                    :max="10"
                    @update:model-value="(value) => updateRateCount(question, value)"
                  />
                </div>

                <div style="color: #999;">
                  最高分值：{{ getRateMax(question) }}
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <div style="display: flex; gap: 12px;">
          <el-button type="primary" :loading="loading" @click="handleSaveEdit">
            保存修改
          </el-button>
          <el-button @click="handleBack">
            返回列表
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>



