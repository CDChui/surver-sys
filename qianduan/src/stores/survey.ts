import { defineStore } from 'pinia'

export type SurveyStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type QuestionType = 'single' | 'multi' | 'text' | 'textarea' | 'rate'

export interface QuestionOption {
  id: number
  label: string
}

export interface QuestionItem {
  id: number
  type: QuestionType
  title: string
  required: boolean
  options: QuestionOption[]
  min?: number
  max?: number
}

export interface SurveyItem {
  id: number
  title: string
  description: string
  status: SurveyStatus
  createdAt: string
  creatorId: number
  allowDuplicateSubmit: boolean
  schema: QuestionItem[]
}

const STORAGE_KEY = 'SURVEY_DETAIL_MAP'
const LIST_KEY = 'SURVEY_LIST'

function getDefaultSurveyMap(): Record<number, SurveyItem> {
  const defaultSurvey: SurveyItem = {
    id: 1,
    title: '测试问卷',
    description: '测试',
    status: 'DRAFT',
    createdAt: '2026-03-08 09:00:00',
    creatorId: 1,
    allowDuplicateSubmit: false,
    schema: [
      {
        id: 1,
        type: 'single',
        title: '什么颜色',
        required: true,
        options: [
          { id: 1, label: '红色' },
          { id: 2, label: '黄色' },
          { id: 3, label: '蓝色' }
        ]
      },
      {
        id: 2,
        type: 'multi',
        title: '请填写多选题标题',
        required: true,
        options: [
          { id: 1, label: '选项1' },
          { id: 2, label: '选项2' },
          { id: 3, label: '选项3' },
          { id: 4, label: '选项4' }
        ]
      },
      {
        id: 3,
        type: 'rate',
        title: '请填写评分题标题',
        required: true,
        options: []
      },
      {
        id: 4,
        type: 'text',
        title: '请填写填空题标题',
        required: false,
        options: []
      }
    ]
  }

  return {
    1: defaultSurvey
  }
}

function getDefaultSurveyList() {
  return [
    {
      id: 1,
      title: '测试问卷',
      status: 'DRAFT' as SurveyStatus,
      createdAt: '2026-03-08 09:00:00',
      creatorId: 1
    }
  ]
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultSurveyMap()))
  }

  if (!localStorage.getItem(LIST_KEY)) {
    localStorage.setItem(LIST_KEY, JSON.stringify(getDefaultSurveyList()))
  }
}

function readSurveyMap(): Record<number, SurveyItem> {
  ensureStorage()
  return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
}

function writeSurveyMap(map: Record<number, SurveyItem>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

function writeSurveyListFromMap(map: Record<number, SurveyItem>) {
  const list = Object.values(map).map((item) => ({
    id: item.id,
    title: item.title,
    status: item.status,
    createdAt: item.createdAt,
    creatorId: item.creatorId
  }))

  localStorage.setItem(LIST_KEY, JSON.stringify(list))
}

export const useSurveyStore = defineStore('survey', {
  state: () => ({
    surveyMap: readSurveyMap() as Record<number, SurveyItem>
  }),

  getters: {
    surveyList(state) {
      return Object.values(state.surveyMap).map((item) => ({
        id: item.id,
        title: item.title,
        status: item.status,
        createdAt: item.createdAt,
        creatorId: item.creatorId
      }))
    }
  },

  actions: {
    persist() {
      writeSurveyMap(this.surveyMap)
      writeSurveyListFromMap(this.surveyMap)
    },

    createSurvey(payload: {
      id: number
      title: string
      description: string
      schema: QuestionItem[]
      creatorId: number
      allowDuplicateSubmit?: boolean
    }) {
      this.surveyMap[payload.id] = {
        id: payload.id,
        title: payload.title,
        description: payload.description,
        status: 'DRAFT',
        createdAt: new Date().toLocaleString('zh-CN', { hour12: false }),
        creatorId: payload.creatorId,
        allowDuplicateSubmit: Boolean(payload.allowDuplicateSubmit),
        schema: payload.schema
      }

      this.persist()
    },

    updateSurvey(payload: {
      id: number
      title: string
      description: string
      schema: QuestionItem[]
      allowDuplicateSubmit?: boolean
    }) {
      const target = this.surveyMap[payload.id]
      if (!target) return

      this.surveyMap[payload.id] = {
        ...target,
        title: payload.title,
        description: payload.description,
        allowDuplicateSubmit: Boolean(payload.allowDuplicateSubmit),
        schema: payload.schema
      }

      this.persist()
    },

    publishSurvey(id: number) {
      const target = this.surveyMap[id]
      if (!target) return

      this.surveyMap[id] = {
        ...target,
        status: 'PUBLISHED'
      }

      this.persist()
    },

    closeSurvey(id: number) {
      const target = this.surveyMap[id]
      if (!target) return

      this.surveyMap[id] = {
        ...target,
        status: 'CLOSED'
      }

      this.persist()
    },

    deleteSurvey(id: number) {
      delete this.surveyMap[id]
      this.persist()
    }
  }
})
