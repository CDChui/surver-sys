import request from './request'
import { USE_REAL_API } from '../config/env'
import { useSurveyAuthStore } from '../stores/survey-auth'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface SurveyAuthUserResult {
  userId: number
  username: string
  realName: string
}

export async function getSurveyAuthUsers(
  surveyId: number
): Promise<ApiResponse<SurveyAuthUserResult[]>> {
  if (USE_REAL_API) {
    return request.get(`/surveys/${surveyId}/auth`)
  }

  const surveyAuthStore = useSurveyAuthStore()

  return {
    code: 20000,
    message: 'success',
    data: surveyAuthStore.getUsersBySurveyId(surveyId)
  }
}

export async function addSurveyAuthUser(
  surveyId: number,
  user: SurveyAuthUserResult
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/surveys/${surveyId}/auth`, user)
  }

  const surveyAuthStore = useSurveyAuthStore()
  surveyAuthStore.addUserToSurvey(surveyId, user)

  return {
    code: 20000,
    message: '授权成功',
    data: null
  }
}

export async function removeSurveyAuthUser(
  surveyId: number,
  userId: number
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.delete(`/surveys/${surveyId}/auth/${userId}`)
  }

  const surveyAuthStore = useSurveyAuthStore()
  surveyAuthStore.removeUserFromSurvey(surveyId, userId)

  return {
    code: 20000,
    message: '撤销成功',
    data: null
  }
}