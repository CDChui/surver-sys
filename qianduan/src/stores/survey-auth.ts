import { defineStore } from 'pinia'

const STORAGE_KEY = 'SURVEY_AUTH_MAP'

export interface SurveyAuthUserItem {
  userId: number
  username: string
  realName: string
}

function readMap(): Record<number, SurveyAuthUserItem[]> {
  return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
}

function writeMap(map: Record<number, SurveyAuthUserItem[]>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

export const useSurveyAuthStore = defineStore('surveyAuth', {
  state: () => ({
    surveyAuthMap: readMap() as Record<number, SurveyAuthUserItem[]>
  }),

  actions: {
    getUsersBySurveyId(surveyId: number) {
      return [...(this.surveyAuthMap[surveyId] || [])]
    },

    setUsersBySurveyId(surveyId: number, users: SurveyAuthUserItem[]) {
      this.surveyAuthMap = {
        ...this.surveyAuthMap,
        [surveyId]: [...users]
      }

      writeMap(this.surveyAuthMap)
    },

    addUserToSurvey(
      surveyId: number,
      user: SurveyAuthUserItem
    ) {
      const currentUsers = this.getUsersBySurveyId(surveyId)

      const exists = currentUsers.some((item) => item.userId === user.userId)
      if (exists) return false

      this.setUsersBySurveyId(surveyId, [...currentUsers, user])
      return true
    },

    removeUserFromSurvey(surveyId: number, userId: number) {
      const currentUsers = this.getUsersBySurveyId(surveyId)
      const nextUsers = currentUsers.filter((item) => item.userId !== userId)

      this.setUsersBySurveyId(surveyId, nextUsers)
      return true
    }
  }
})