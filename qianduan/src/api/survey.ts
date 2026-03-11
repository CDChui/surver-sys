import request from './request'
import axios from 'axios'
import * as XLSX from 'xlsx'
import { API_BASE, USE_REAL_API } from '../config/env'
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
  min?: number
  max?: number
}

export interface CreateSurveyDraftParams {
  title: string
  description: string
  questions: QuestionSchemaItem[]
  allowDuplicateSubmit: boolean
}

export interface CreateSurveyDraftResult {
  id: number
  title: string
  description: string
  status: 'DRAFT'
  schema: QuestionSchemaItem[]
  creatorId: number
  allowDuplicateSubmit?: boolean
}

export interface SurveyDetailResult {
  id: number
  title: string
  description: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  schema: QuestionSchemaItem[]
  creatorId: number
  allowDuplicateSubmit?: boolean
}

export interface UpdateSurveyParams {
  id: number
  title: string
  description: string
  questions: QuestionSchemaItem[]
  allowDuplicateSubmit: boolean
}

export interface PublicSurveyResult {
  id: number
  title: string
  description: string
  schema: QuestionSchemaItem[]
  entryToken?: string
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
  responseCount: number
  schema: QuestionSchemaItem[]
  statsList: SurveyStatsItem[]
}

export interface SubmitSurveyParams {
  surveyId: number
  answers: Record<number, string | string[] | number>
  entryToken?: string
  previewMode?: boolean
}

export interface SubmitSurveyResult {
  surveyId: number
  submitTime: string
}

export interface MySurveySubmissionItemResult {
  surveyId: number
  surveyTitle: string
  submitTime: string
}

export interface MySurveySubmissionDetailResult {
  surveyId: number
  surveyTitle: string
  surveyDescription: string
  schema: QuestionSchemaItem[]
  answers: Record<string, string | string[] | number>
  submitTime: string
}

export interface SurveyResponseItemResult {
  userId: number
  account: string
  username: string
  submitTime: string
  terminalType: string
  sourceType?: string
  sourceIp: string
  answers: Record<string, string | string[] | number>
}

export interface SurveyResponseListResult {
  surveyId: number
  surveyTitle: string
  surveyDescription: string
  schema: QuestionSchemaItem[]
  responses: SurveyResponseItemResult[]
}

interface SubmittedSnapshotStorage {
  surveyId: number
  surveyTitle: string
  surveyDescription: string
  schema: QuestionSchemaItem[]
  answers: Record<string, string | string[] | number>
  userId?: number | null
  username?: string
  submitTime?: string
}

const DETAIL_KEY = 'SURVEY_DETAIL_MAP'
const LIST_KEY = 'SURVEY_LIST'

function readDetailMap(): Record<number, SurveyDetailResult> {
  return JSON.parse(localStorage.getItem(DETAIL_KEY) || '{}')
}

function readSurveyList(): SurveyListItemResult[] {
  return JSON.parse(localStorage.getItem(LIST_KEY) || '[]')
}

function writeDetailMap(map: Record<number, SurveyDetailResult>) {
  localStorage.setItem(DETAIL_KEY, JSON.stringify(map))
}

function writeSurveyList(list: SurveyListItemResult[]) {
  localStorage.setItem(LIST_KEY, JSON.stringify(list))
}

function sanitizeStorageKeyPart(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '_')
}

function getStorageUserScope() {
  const authStore = useAuthStore()

  if (typeof authStore.userId === 'number' && Number.isFinite(authStore.userId)) {
    return `uid_${authStore.userId}`
  }

  const localUserId = String(localStorage.getItem('AUTH_USER_ID') || '').trim()
  if (localUserId && /^\d+$/.test(localUserId)) {
    return `uid_${localUserId}`
  }

  const username = String(authStore.username || localStorage.getItem('AUTH_USERNAME') || '').trim()
  if (username) {
    return `name_${sanitizeStorageKeyPart(username)}`
  }

  return 'anon'
}

function getSubmittedStoragePrefix() {
  return `SURVEY_SUBMITTED_${getStorageUserScope()}_`
}

function normalizeSubmittedSnapshot(raw: unknown): SubmittedSnapshotStorage | null {
  if (!raw || typeof raw !== 'object') return null

  const candidate = raw as Partial<SubmittedSnapshotStorage>
  const surveyId = Number(candidate.surveyId)
  if (!Number.isFinite(surveyId) || surveyId <= 0) return null

  const title = String(candidate.surveyTitle || '').trim()
  const description = String(candidate.surveyDescription || '')
  const schema = Array.isArray(candidate.schema) ? candidate.schema : []
  const answersRaw = candidate.answers
  const answers: Record<string, string | string[] | number> = {}
  const userIdRaw = Number(candidate.userId)
  const userId =
    Number.isFinite(userIdRaw) && userIdRaw > 0
      ? userIdRaw
      : null
  const username = String(candidate.username || '').trim()

  if (answersRaw && typeof answersRaw === 'object') {
    Object.entries(answersRaw as Record<string, unknown>).forEach(([key, value]) => {
      if (
        typeof value === 'string' ||
        typeof value === 'number' ||
        (Array.isArray(value) && value.every((item) => typeof item === 'string'))
      ) {
        answers[key] = value
      }
    })
  }

  return {
    surveyId,
    surveyTitle: title || `问卷${surveyId}`,
    surveyDescription: description,
    schema,
    answers,
    userId,
    username,
    submitTime: typeof candidate.submitTime === 'string' ? candidate.submitTime : ''
  }
}

function listSubmittedSnapshotsFromStorage() {
  const snapshots: SubmittedSnapshotStorage[] = []
  const prefix = getSubmittedStoragePrefix()

  for (let i = 0; i < localStorage.length; i += 1) {
    const key = localStorage.key(i)
    if (!key || !key.startsWith(prefix)) continue

    const raw = localStorage.getItem(key)
    if (!raw) continue

    try {
      const parsed = JSON.parse(raw)
      const normalized = normalizeSubmittedSnapshot(parsed)
      if (normalized) {
        snapshots.push(normalized)
      }
    } catch (error) {
      // ignore invalid cache payload
    }
  }

  return snapshots
}

function getSubmittedSnapshotFromStorage(surveyId: number) {
  const raw = localStorage.getItem(`${getSubmittedStoragePrefix()}${surveyId}`)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw)
    return normalizeSubmittedSnapshot(parsed)
  } catch (error) {
    return null
  }
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

function buildMockEntryToken(surveyId: number) {
  return `mock-entry-${surveyId}-${Date.now()}`
}

function triggerFileDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

function parseFileNameFromDisposition(
  dispositionValue: string | undefined,
  fallbackName: string
) {
  if (!dispositionValue) return fallbackName

  const utf8Matched = dispositionValue.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Matched?.[1]) {
    try {
      return decodeURIComponent(utf8Matched[1].trim())
    } catch (error) {
      return utf8Matched[1].trim()
    }
  }

  const asciiMatched = dispositionValue.match(/filename=\"?([^\"]+)\"?/i)
  if (asciiMatched?.[1]) {
    return asciiMatched[1].trim()
  }

  return fallbackName
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
        textSummary: '当前为演示版统计：这里后续可展示高频关键词、示例回答、回答数量等信息。'
      }
    }

    if (question.type === 'rate') {
      const min = typeof question.min === 'number' ? question.min : 1
      const max = typeof question.max === 'number' ? question.max : 5
      const safeMin = Number.isFinite(min) ? Math.floor(min) : 1
      const safeMax = Number.isFinite(max) ? Math.max(Math.floor(max), safeMin) : safeMin + 4
      const rateStats = []
      for (let score = safeMin; score <= safeMax; score += 1) {
        rateStats.push({ score, count: (score - safeMin + 1) * 3 })
      }
      const avgScore =
        rateStats.length > 0 ? Number(((safeMin + safeMax) / 2).toFixed(2)) : 0
      return {
        id: question.id,
        title: question.title,
        type: question.type,
        required: question.required,
        avgScore,
        rateStats
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

type MockSubmissionRecord = {
  submitTime: string
  account: string
  username: string
  answers: Record<string, string | string[] | number>
}

function buildMockSubmissionRecords(survey: SurveyDetailResult): MockSubmissionRecord[] {
  const questionMap = survey.schema

  return [
    {
      submitTime: '2026-03-08 09:10:22',
      account: 'student01',
      username: '王同学',
      answers: {
        [String(questionMap[0]?.id ?? 0)]: '红色',
        [String(questionMap[1]?.id ?? 0)]: ['选项1', '选项3'],
        [String(questionMap[2]?.id ?? 0)]: 4,
        [String(questionMap[3]?.id ?? 0)]: '这里是第一位用户的填空内容'
      }
    },
    {
      submitTime: '2026-03-08 09:26:48',
      account: 'student02',
      username: '赵同学',
      answers: {
        [String(questionMap[0]?.id ?? 0)]: '黄色',
        [String(questionMap[1]?.id ?? 0)]: ['选项2'],
        [String(questionMap[2]?.id ?? 0)]: 5,
        [String(questionMap[3]?.id ?? 0)]: '这里是第二位用户的填空内容'
      }
    },
    {
      submitTime: '2026-03-08 10:03:11',
      account: 'student03',
      username: '李同学',
      answers: {
        [String(questionMap[0]?.id ?? 0)]: '蓝色',
        [String(questionMap[1]?.id ?? 0)]: ['选项1', '选项2', '选项4'],
        [String(questionMap[2]?.id ?? 0)]: 3,
        [String(questionMap[3]?.id ?? 0)]: '这里是第三位用户的填空内容'
      }
    }
  ]
}

function buildMockSubmissionRows(survey: SurveyDetailResult) {
  return buildMockSubmissionRecords(survey).map((user) => {
    const row: Record<string, string | number> = {
      '提交时间': user.submitTime,
      '账号': user.account,
      '用户名': user.username
    }

    survey.schema.forEach((question) => {
      const rawValue = user.answers[String(question.id)]

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
          creatorId,
          allowDuplicateSubmit: params.allowDuplicateSubmit
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
          creatorId: target.creatorId,
          allowDuplicateSubmit: params.allowDuplicateSubmit
        }
      })
    }, 500)
  })
}

export async function publishSurveyApi(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/surveys/${id}/publish`)
  }

  const detailMap = readDetailMap()
  const target = detailMap[id]

  if (!target) {
    return {
      code: 40404,
      message: '问卷不存在',
      data: null
    }
  }

  detailMap[id] = {
    ...target,
    status: 'PUBLISHED'
  }
  writeDetailMap(detailMap)

  const nextList = readSurveyList().map((item) =>
    item.id === id
      ? {
          ...item,
          status: 'PUBLISHED' as const
        }
      : item
  )
  writeSurveyList(nextList)

  return {
    code: 20000,
    message: '发布成功',
    data: null
  }
}

export async function closeSurveyApi(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/surveys/${id}/close`)
  }

  const detailMap = readDetailMap()
  const target = detailMap[id]

  if (!target) {
    return {
      code: 40404,
      message: '问卷不存在',
      data: null
    }
  }

  detailMap[id] = {
    ...target,
    status: 'CLOSED'
  }
  writeDetailMap(detailMap)

  const nextList = readSurveyList().map((item) =>
    item.id === id
      ? {
          ...item,
          status: 'CLOSED' as const
        }
      : item
  )
  writeSurveyList(nextList)

  return {
    code: 20000,
    message: '关闭成功',
    data: null
  }
}

export async function deleteSurveyApi(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.delete(`/surveys/${id}`)
  }

  const detailMap = readDetailMap()
  const target = detailMap[id]

  if (!target) {
    return {
      code: 40404,
      message: '问卷不存在',
      data: null
    }
  }

  delete detailMap[id]
  writeDetailMap(detailMap)
  writeSurveyList(readSurveyList().filter((item) => item.id !== id))

  return {
    code: 20000,
    message: '删除成功',
    data: null
  }
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
          responseCount: buildMockSubmissionRecords(target).length,
          schema: target.schema,
          statsList: buildMockStats(target.schema)
        }
      })
    }, 500)
  })
}

export async function getPublicSurvey(
  id: number,
  options?: {
    mode?: 'normal' | 'quota' | 'duplicate'
    previewMode?: boolean
  }
): Promise<ApiResponse<PublicSurveyResult | null>> {
  const mode = options?.mode || 'normal'
  const previewMode = Boolean(options?.previewMode)

  if (USE_REAL_API) {
    return request.get(`/surveys/${id}/public`, {
      params: {
        previewMode
      }
    })
  }

  return new Promise((resolve) => {
    setTimeout(() => {
      if (!previewMode && mode === 'quota') {
        resolve({
          code: 40011,
          message: '当前问卷名额已满',
          data: null
        })
        return
      }

      if (!previewMode && mode === 'duplicate') {
        resolve({
          code: 40009,
          message: '你已经提交过该问卷',
          data: null
        })
        return
      }

      const detailMap = readDetailMap()
      const target = detailMap[id]
      const authStore = useAuthStore()

      if (!target) {
        resolve({
          code: 40404,
          message: '问卷不存在或不可访问',
          data: null
        })
        return
      }

      const isManagerRole = authStore.role === 'ROLE2' || authStore.role === 'ROLE3'
      if (target.status !== 'PUBLISHED' && !isManagerRole) {
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
          schema: target.schema,
          entryToken: previewMode ? '' : buildMockEntryToken(target.id)
        }
      })
    }, 500)
  })
}

export async function getSurveyResponses(
  id: number
): Promise<ApiResponse<SurveyResponseListResult>> {
  if (USE_REAL_API) {
    return request.get(`/surveys/${id}/responses`)
  }

  try {
    const detailResponse = await getSurveyDetail(id)
    if (detailResponse.code !== 20000) {
      return {
        code: detailResponse.code,
        message: detailResponse.message,
        data: null as unknown as SurveyResponseListResult
      }
    }

    const survey = detailResponse.data
    const mockTerminalTypes = ['移动端', 'PC', '平板']
    const mockIps = ['192.168.1.22', '10.0.0.15', '172.16.2.8']
    const mockSources = ['微信', '直接链接']
    const responses = buildMockSubmissionRecords(survey).map((record, index) => ({
      userId: index + 1,
      account: record.account,
      username: record.username,
      submitTime: record.submitTime,
      terminalType: mockTerminalTypes[index % mockTerminalTypes.length],
      sourceType: mockSources[index % mockSources.length],
      sourceIp: mockIps[index % mockIps.length],
      answers: record.answers
    }))

    return {
      code: 20000,
      message: 'success',
      data: {
        surveyId: survey.id,
        surveyTitle: survey.title,
        surveyDescription: survey.description,
        schema: survey.schema,
        responses
      }
    }
  } catch (error) {
    return {
      code: 40404,
      message: '问卷不存在',
      data: null as unknown as SurveyResponseListResult
    }
  }
}

export async function submitSurvey(
  params: SubmitSurveyParams
): Promise<ApiResponse<SubmitSurveyResult>> {
  if (USE_REAL_API) {
    return request.post(`/surveys/${params.surveyId}/responses`, params)
  }

  const detailMap = readDetailMap()
  const target = detailMap[params.surveyId]
  const authStore = useAuthStore()

  if (!target) {
    return {
      code: 40404,
      message: '问卷不存在或不可访问',
      data: null as unknown as SubmitSurveyResult
    }
  }

  const isManagerRole = authStore.role === 'ROLE2' || authStore.role === 'ROLE3'
  if (target.status !== 'PUBLISHED' && !isManagerRole) {
    return {
      code: 40404,
      message: '问卷不存在或不可访问',
      data: null as unknown as SubmitSurveyResult
    }
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

export async function getMySurveySubmissions(): Promise<ApiResponse<MySurveySubmissionItemResult[]>> {
  if (USE_REAL_API) {
    const response = await request.get('/surveys/my/submissions')
    const message = String(response?.message || '')

    if (
      response?.code === 50000 &&
      /No static resource\s+api\/surveys\/my\/submissions/i.test(message)
    ) {
      const list = listSubmittedSnapshotsFromStorage()
        .map((item) => ({
          surveyId: item.surveyId,
          surveyTitle: item.surveyTitle,
          submitTime: item.submitTime || ''
        }))
        .sort((a, b) => {
          if (a.submitTime && b.submitTime) {
            return b.submitTime.localeCompare(a.submitTime)
          }
          return b.surveyId - a.surveyId
        })

      return {
        code: 20000,
        message: 'LOCAL_FALLBACK_MISSING_ENDPOINT',
        data: list
      }
    }

    return response
  }

  const list = listSubmittedSnapshotsFromStorage()
    .map((item) => ({
      surveyId: item.surveyId,
      surveyTitle: item.surveyTitle,
      submitTime: item.submitTime || ''
    }))
    .sort((a, b) => {
      if (a.submitTime && b.submitTime) {
        return b.submitTime.localeCompare(a.submitTime)
      }
      return b.surveyId - a.surveyId
    })

  return {
    code: 20000,
    message: 'success',
    data: list
  }
}

export async function getMySurveySubmissionDetail(
  surveyId: number
): Promise<ApiResponse<MySurveySubmissionDetailResult>> {
  if (USE_REAL_API) {
    const response = await request.get(`/surveys/${surveyId}/my-submission`)
    const message = String(response?.message || '')

    if (
      response?.code === 50000 &&
      /No static resource\s+api\/surveys\/\d+\/my-submission/i.test(message)
    ) {
      const snapshot = getSubmittedSnapshotFromStorage(surveyId)

      if (!snapshot) {
        return {
          code: 40404,
          message: '后端缺少答卷回看接口且本地无缓存，请重启后端后再试',
          data: null as unknown as MySurveySubmissionDetailResult
        }
      }

      return {
        code: 20000,
        message: 'LOCAL_FALLBACK_MISSING_ENDPOINT',
        data: {
          surveyId: snapshot.surveyId,
          surveyTitle: snapshot.surveyTitle,
          surveyDescription: snapshot.surveyDescription,
          schema: snapshot.schema,
          answers: snapshot.answers,
          submitTime: snapshot.submitTime || ''
        }
      }
    }

    return response
  }

  const snapshot = getSubmittedSnapshotFromStorage(surveyId)

  if (!snapshot) {
    return {
      code: 40404,
      message: '未找到已提交记录',
      data: null as unknown as MySurveySubmissionDetailResult
    }
  }

  return {
    code: 20000,
    message: 'success',
    data: {
      surveyId: snapshot.surveyId,
      surveyTitle: snapshot.surveyTitle,
      surveyDescription: snapshot.surveyDescription,
      schema: snapshot.schema,
      answers: snapshot.answers,
      submitTime: snapshot.submitTime || ''
    }
  }
}

export async function exportSurveyStatsExcel(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    const token = localStorage.getItem('AUTH_TOKEN')
    const response = await axios.get(`${API_BASE}/surveys/${id}/export`, {
      responseType: 'blob',
      headers: token
        ? {
            Authorization: `Bearer ${token}`
          }
        : undefined
    })

    const disposition =
      response.headers['content-disposition'] ||
      response.headers['Content-Disposition']
    const filename = parseFileNameFromDisposition(disposition, `survey-${id}.xlsx`)

    triggerFileDownload(response.data, filename)

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

