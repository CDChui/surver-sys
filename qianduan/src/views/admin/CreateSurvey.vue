<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createSurveyDraft,
  type QuestionOption,
  type QuestionSchemaItem,
  type QuestionType
} from '../../api/survey'
import { useSurveyStore } from '../../stores/survey'
import { appendOperationLog } from '../../utils/log'

const router = useRouter()
const surveyStore = useSurveyStore()
const loading = ref(false)

const form = reactive({
  title: '',
  description: '',
  questions: [] as QuestionSchemaItem[]
})

const schemaPreview = computed(() => {
  return JSON.stringify(
    {
      title: form.title,
      description: form.description,
      questions: form.questions
    },
    null,
    2
  )
})

async function handleSaveDraft() {
  if (!form.title) {
    alert('请输入问卷标题')
    return
  }

  try {
    loading.value = true

    const response = await createSurveyDraft({
      title: form.title,
      description: form.description,
      questions: form.questions
    })

    const result = response.data

    surveyStore.createSurvey({
      id: result.id,
      title: result.title,
      description: result.description,
      schema: result.schema,
      creatorId: result.creatorId
    })

    appendOperationLog({
      module: 'SURVEY',
      action: 'CREATE',
      target: result.title
    })

    alert(
      `草稿已保存，问卷ID：${result.id}，题目数：${result.schema.length}`
    )

    router.push('/admin')
  } catch (error) {
    alert('保存草稿失败')
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
  router.push('/admin')
}
</script>

<template>
  <div style="padding: 24px; min-height: 100vh; background: #f5f7fa;">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <h2 style="margin: 0;">创建问卷</h2>
            <p style="margin: 8px 0 0; color: #666;">先填写基础信息，再添加题目</p>
          </div>

          <el-button @click="handleBack">
            返回列表
          </el-button>
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
          </el-card>
        </div>

        <div style="margin-bottom: 24px;">
          <div style="margin-bottom: 8px; font-weight: 600;">当前 Schema 预览</div>
          <el-input
            :model-value="schemaPreview"
            type="textarea"
            :rows="16"
            readonly
          />
        </div>

        <div style="display: flex; gap: 12px;">
          <el-button type="primary" :loading="loading" @click="handleSaveDraft">
            保存草稿
          </el-button>

          <el-button @click="handleBack">
            返回列表
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>
