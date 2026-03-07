import request from './request'
import * as XLSX from 'xlsx'
import { USE_REAL_API } from '../config/env'
import { useAuthStore } from '../stores/auth'
import { useSurveyAuthStore } from '../stores/survey-auth'

export type QuestionType = 'single' | 'multi' | 'text' | 'textarea' | 'rate'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface QuestionOption {
  id: number
  label: string
}

export interface QuestionSchemaItem {
  id: number
  type: QuestionType
  title: string
  required: boolean
  options: QuestionOption[]
}

export interface CreateSurveyDraftParams {
  title: string
  description: string
  questions: QuestionSchemaItem[]
}

export interface CreateSurveyDraftResult {
  id: number
  title: string
  description: string
  status: 'DRAFT'
  schema: QuestionSchemaItem[]
  creatorId: number
}

export interface SurveyDetailResult {
  id: number
  title: string
  description: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  schema: QuestionSchemaItem[]
  creatorId: number
}

export interface UpdateSurveyParams {
  id: number
  title: string
  description: string
  questions: QuestionSchemaItem[]
}

export interface PublicSurveyResult {
  id: number
  title: string
  description: string
  schema: QuestionSchemaItem[]
}

export interface SurveyListItemResult {
  id: number
  title: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  createdAt: string
  creatorId: number
}

export interface SurveyStatsOptionItem {
  label: string
  count: number
}

export interface SurveyStatsItem {
  id: number
  title: string
  type: string
  required: boolean
  optionStats?: SurveyStatsOptionItem[]
  textSummary?: string
  avgScore?: number
  rateStats?: { score: number; count: number }[]
}

export interface SurveyStatsResult {
  id: number
  title: string
  description: string
  schema: QuestionSchemaItem[]
  statsList: SurveyStatsItem[]
}

export interface SubmitSurveyParams {
  surveyId: number
  answers: Record<number, string | string[] | number>
}

export interface SubmitSurveyResult {
  surveyId: number
  submitTime: string
}

const DETAIL_KEY = 'SURVEY_DETAIL_MAP'
const LIST_KEY = 'SURVEY_LIST'

function readDetailMap(): Record<number, SurveyDetailResult> {
  return JSON.parse(localStorage.getItem(DETAIL_KEY) || '{}')
}

function readSurveyList(): SurveyListItemResult[] {
  return JSON.parse(localStorage.getItem(LIST_KEY) || '[]')
}

function getNowText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}:${ss}`
}

function getDateText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function buildMockStats(questions: QuestionSchemaItem[]): SurveyStatsItem[] {
  return questions.map((question) => {
    if (question.type === 'single') {
      return {
        id: question.id,
        title: question.title,
        type: question.type,
        required: question.required,
        optionStats: question.options.map((option, index) => ({
          label: option.label,
          count: (index + 1) * 10
        }))
      }
    }

    if (question.type === 'multi') {
      return {
        id: question.id,
        title: question.title,
        type: question.type,
        required: question.required,
        optionStats: question.options.map((option, index) => ({
          label: option.label,
          count: (index + 2) * 7
        }))
      }
    }

    if (question.type === 'text' || question.type === 'textarea') {
      return {
        id: question.id,
        title: question.title,
        type: question.type,
        required: question.required,
        textSummary: '当前为演示版统计：这里后续可展示高频关键词、示例回答、回答数等信息。'
      }
    }

    if (question.type === 'rate') {
      return {
        id: question.id,
        title: question.title,
        type: question.type,
        required: question.required,
        avgScore: 4.2,
        rateStats: [
          { score: 1, count: 1 },
          { score: 2, count: 2 },
          { score: 3, count: 6 },
          { score: 4, count: 10 },
          { score: 5, count: 13 }
        ]
      }
    }

    return {
      id: question.id,
      title: question.title,
      type: question.type,
      required: question.required
    }
  })
}

function buildMockSubmissionRows(survey: SurveyDetailResult) {
  const questionMap = survey.schema

  const mockUsers = [
    {
      submitTime: '2026-03-08 09:10:22',
      account: 'student01',
      username: '王同学',
      answers: {
        [questionMap[0]?.id ?? 0]: '红色',
        [questionMap[1]?.id ?? 0]: ['选项1', '选项3'],
        [questionMap[2]?.id ?? 0]: 4,
        [questionMap[3]?.id ?? 0]: '这里是第一位用户的填空内容'
      }
    },
    {
      submitTime: '2026-03-08 09:26:48',
      account: 'student02',
      username: '赵同学',
      answers: {
        [questionMap[0]?.id ?? 0]: '黄色',
        [questionMap[1]?.id ?? 0]: ['选项2'],
        [questionMap[2]?.id ?? 0]: 5,
        [questionMap[3]?.id ?? 0]: '这里是第二位用户的填空内容'
      }
    },
    {
      submitTime: '2026-03-08 10:03:11',
      account: 'student03',
      username: '李同学',
      answers: {
        [questionMap[0]?.id ?? 0]: '蓝色',
        [questionMap[1]?.id ?? 0]: ['选项1', '选项2', '选项4'],
        [questionMap[2]?.id ?? 0]: 3,
        [questionMap[3]?.id ?? 0]: '这里是第三位用户的填空内容'
      }
    }
  ]

  return mockUsers.map((user) => {
    const row: Record<string, string | number> = {
      提交时间: user.submitTime,
      账号: user.account,
      用户名: user.username
    }

    survey.schema.forEach((question) => {
      const rawValue = user.answers[question.id as keyof typeof user.answers]

      if (Array.isArray(rawValue)) {
        row[question.title] = rawValue.join('、')
      } else if (rawValue === undefined || rawValue === null || rawValue === '') {
        row[question.title] = ''
      } else {
        row[question.title] = rawValue
      }
    })

    return row
  })
}

function hasSurveyPermission(surveyId: number) {
  const authStore = useAuthStore()
  const surveyAuthStore = useSurveyAuthStore()
  const detailMap = readDetailMap()
  const target = detailMap[surveyId]

  if (!target) return false
  if (authStore.isRole3) return true
  if (authStore.isRole1) return false

  if (authStore.userId && target.creatorId === authStore.userId) {
    return true
  }

  const authUsers = surveyAuthStore.getUsersBySurveyId(surveyId)
  return authUsers.some((item) => item.userId === authStore.userId)
}

export async function createSurveyDraft(
  params: CreateSurveyDraftParams
): Promise<ApiResponse<CreateSurveyDraftResult>> {
  if (USE_REAL_API) {
    return request.post('/surveys', params)
  }

  const authStore = useAuthStore()
  const creatorId = authStore.userId || 1

  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        code: 20000,
        message: 'success',
        data: {
          id: Date.now(),
          title: params.title,
          description: params.description,
          status: 'DRAFT',
          schema: params.questions,
          creatorId
        }
      })
    }, 500)
  })
}

export async function getSurveyDetail(
  id: number
): Promise<ApiResponse<SurveyDetailResult>> {
  if (USE_REAL_API) {
    return request.get(`/surveys/${id}`)
  }

  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const detailMap = readDetailMap()
      const target = detailMap[id]

      if (!target) {
        reject(new Error('问卷不存在'))
        return
      }

      if (!hasSurveyPermission(id)) {
        resolve({
          code: 40301,
          message: '无权限访问该问卷',
          data: target
        })
        return
      }

      resolve({
        code: 20000,
        message: 'success',
        data: target
      })
    }, 500)
  })
}

export async function updateSurvey(
  params: UpdateSurveyParams
): Promise<ApiResponse<SurveyDetailResult>> {
  if (USE_REAL_API) {
    return request.put(`/surveys/${params.id}`, params)
  }

  const detailMap = readDetailMap()
  const target = detailMap[params.id]

  if (!target) {
    return {
      code: 40404,
      message: '问卷不存在',
      data: null as unknown as SurveyDetailResult
    }
  }

  if (!hasSurveyPermission(params.id)) {
    return {
      code: 40301,
      message: '无权限修改该问卷',
      data: target
    }
  }

  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        code: 20000,
        message: 'success',
        data: {
          id: params.id,
          title: params.title,
          description: params.description,
          status: target.status,
          schema: params.questions,
          creatorId: target.creatorId
        }
      })
    }, 500)
  })
}

export async function getSurveyList(): Promise<ApiResponse<SurveyListItemResult[]>> {
  if (USE_REAL_API) {
    return request.get('/surveys')
  }

  const authStore = useAuthStore()
  const surveyAuthStore = useSurveyAuthStore()

  return new Promise((resolve) => {
    setTimeout(() => {
      const allList = readSurveyList()

      if (authStore.isRole3) {
        resolve({
          code: 20000,
          message: 'success',
          data: allList
        })
        return
      }

      if (authStore.isRole1) {
        resolve({
          code: 40301,
          message: '普通用户无权访问后台问卷列表',
          data: []
        })
        return
      }

      const currentUserId = authStore.userId
      const allowedSurveyIds = new Set<number>()

      allList.forEach((item) => {
        if (item.creatorId === currentUserId) {
          allowedSurveyIds.add(item.id)
        }
      })

      Object.entries(surveyAuthStore.surveyAuthMap).forEach(([surveyId, users]) => {
        const hasMe = users.some((user) => user.userId === currentUserId)
        if (hasMe) {
          allowedSurveyIds.add(Number(surveyId))
        }
      })

      resolve({
        code: 20000,
        message: 'success',
        data: allList.filter((item) => allowedSurveyIds.has(item.id))
      })
    }, 300)
  })
}

export async function getSurveyStats(
  id: number
): Promise<ApiResponse<SurveyStatsResult>> {
  if (USE_REAL_API) {
    return request.get(`/surveys/${id}/stats`)
  }

  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const detailMap = readDetailMap()
      const target = detailMap[id]

      if (!target) {
        reject(new Error('问卷不存在'))
        return
      }

      if (!hasSurveyPermission(id)) {
        resolve({
          code: 40301,
          message: '无权限查看该问卷统计',
          data: null as unknown as SurveyStatsResult
        })
        return
      }

      resolve({
        code: 20000,
        message: 'success',
        data: {
          id: target.id,
          title: target.title,
          description: target.description,
          schema: target.schema,
          statsList: buildMockStats(target.schema)
        }
      })
    }, 500)
  })
}

export async function getPublicSurvey(
  id: number,
  mode: 'normal' | 'quota' | 'duplicate' = 'normal'
): Promise<ApiResponse<PublicSurveyResult | null>> {
  if (USE_REAL_API) {
    return request.get(`/surveys/${id}/public`)
  }

  return new Promise((resolve) => {
    setTimeout(() => {
      if (mode === 'quota') {
        resolve({
          code: 40011,
          message: '当前问卷名额已满',
          data: null
        })
        return
      }

      if (mode === 'duplicate') {
        resolve({
          code: 40009,
          message: '你已经提交过该问卷',
          data: null
        })
        return
      }

      const detailMap = readDetailMap()
      const target = detailMap[id]

      if (!target) {
        resolve({
          code: 40404,
          message: '问卷不存在或不可访问',
          data: null
        })
        return
      }

      resolve({
        code: 20000,
        message: 'success',
        data: {
          id: target.id,
          title: target.title,
          description: target.description,
          schema: target.schema
        }
      })
    }, 500)
  })
}

export async function submitSurvey(
  params: SubmitSurveyParams
): Promise<ApiResponse<SubmitSurveyResult>> {
  if (USE_REAL_API) {
    return request.post(`/surveys/${params.surveyId}/responses`, params)
  }

  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        code: 20000,
        message: '提交成功',
        data: {
          surveyId: params.surveyId,
          submitTime: getNowText()
        }
      })
    }, 500)
  })
}

export async function exportSurveyStatsExcel(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    await request.get(`/surveys/${id}/export`, {
      responseType: 'blob'
    })

    return {
      code: 20000,
      message: '导出成功',
      data: null
    }
  }

  if (!hasSurveyPermission(id)) {
    return {
      code: 40301,
      message: '无权限导出该问卷数据',
      data: null
    }
  }

  const detailResponse = await getSurveyDetail(id)

  if (detailResponse.code !== 20000) {
    return {
      code: detailResponse.code,
      message: detailResponse.message,
      data: null
    }
  }

  const survey = detailResponse.data
  const rows = buildMockSubmissionRows(survey)

  const worksheet = XLSX.utils.json_to_sheet(rows)
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, worksheet, '答卷明细')

  const fileName = `${survey.title}_答卷明细_${getDateText()}.xlsx`
  XLSX.writeFile(workbook, fileName)

  return {
    code: 20000,
    message: '导出成功',
    data: null
  }
}