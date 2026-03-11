import request from './request'
import { USE_REAL_API } from '../config/env'
import { useSurveyStore } from '../stores/survey'
import { useUserStore } from '../stores/user'
import { useLogStore } from '../stores/log'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export type DashboardTrendRange = 'today' | 'week' | 'month'

export interface DashboardTrendItem {
  axis: string[]
  values: number[]
}

export interface DashboardTrendSeries {
  today: DashboardTrendItem
  week: DashboardTrendItem
  month: DashboardTrendItem
}

export interface DashboardDistributionItem {
  name: string
  value: number
}

export interface DashboardRecentSurveyItem {
  id: number
  title: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  createdAt: string
}

export interface DashboardRankingItem {
  id: number
  title: string
  count: number
  percent: number
  rank: number
}

export type DashboardTimelineType = 'create' | 'publish' | 'close' | 'system'

export interface DashboardTimelineItem {
  id: string
  time: string
  text: string
  type: DashboardTimelineType
}

export interface DashboardResult {
  surveyTotal: number
  publishedSurveyTotal: number
  userTotal: number
  enabledUserTotal: number
  recentSurveys: DashboardRecentSurveyItem[]
  trend: DashboardTrendSeries
  terminalStats: DashboardDistributionItem[]
  sourceStats: DashboardDistributionItem[]
  hotSurveyRanking: DashboardRankingItem[]
  operationTimeline: DashboardTimelineItem[]
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function buildTrendAxis(range: DashboardTrendRange) {
  if (range === 'today') {
    return Array.from({ length: 12 }, (_, index) => `${pad2(index * 2)}:00`)
  }

  const dayCount = range === 'week' ? 7 : 30
  const result: string[] = []
  const today = new Date()

  for (let i = dayCount - 1; i >= 0; i -= 1) {
    const day = new Date(today)
    day.setDate(today.getDate() - i)
    result.push(`${pad2(day.getMonth() + 1)}-${pad2(day.getDate())}`)
  }

  return result
}

function buildTrendSeries(
  range: DashboardTrendRange,
  surveyTotal: number,
  publishedTotal: number,
  enabledUserTotal: number
) {
  const axis = buildTrendAxis(range)
  const base = Math.max(
    16,
    surveyTotal * 9 + publishedTotal * 15 + enabledUserTotal * 5
  )

  return axis.map((_, index) => {
    if (range === 'today') {
      return Math.max(
        1,
        Math.round((base / 18) * (0.65 + (Math.sin(index * 0.7) + 1) / 2))
      )
    }

    const trendBoost = range === 'month' ? index * 0.3 : index * 0.9
    return Math.max(
      5,
      Math.round(base * (0.42 + (Math.sin(index * 0.85) + 1) / 3) + trendBoost)
    )
  })
}

function buildTerminalStats(
  surveyTotal: number,
  publishedTotal: number
): DashboardDistributionItem[] {
  const base = Math.max(40, 60 + publishedTotal * 2 - surveyTotal)
  const mobile = Math.min(78, Math.max(40, base))
  const pc = Math.max(10, 100 - mobile)
  const tablet = Math.max(5, Math.round(pc * 0.2))
  const harmony = Math.max(0, Math.round(pc * 0.05))
  return [
    { name: '移动端', value: mobile },
    { name: 'PC', value: Math.max(0, pc - tablet - harmony) },
    { name: '平板', value: tablet },
    { name: '鸿蒙', value: harmony }
  ]
}

function buildSourceStats(
  surveyTotal: number,
  publishedTotal: number
): DashboardDistributionItem[] {
  const wechat = Math.max(20, 45 + publishedTotal * 2 - surveyTotal)
  const direct = Math.max(10, 100 - wechat)
  return [
    { name: '微信', value: wechat },
    { name: '直接链接', value: direct }
  ]
}

function getHeatCount(status: 'DRAFT' | 'PUBLISHED' | 'CLOSED', id: number, index: number) {
  const tail = Number(String(id).slice(-3)) || (index + 1) * 17
  const statusBoost = status === 'PUBLISHED' ? 240 : status === 'CLOSED' ? 140 : 95
  return statusBoost + (tail % 180)
}

function buildMockRanking(list: DashboardRecentSurveyItem[]): DashboardRankingItem[] {
  const rows = list
    .map((item, index) => ({
      id: item.id,
      title: item.title,
      count: getHeatCount(item.status, item.id, index)
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)

  const max = rows[0]?.count || 1

  return rows.map((item, index) => ({
    ...item,
    rank: index + 1,
    percent: Math.max(8, Math.round((item.count / max) * 100))
  }))
}

function parseDateValue(value: string) {
  if (!value) return 0
  const parsed = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

function mapActionText(action: string) {
  if (action === 'CREATE') return '创建'
  if (action === 'UPDATE') return '更新'
  if (action === 'DELETE') return '删除'
  if (action === 'PUBLISH') return '发布'
  if (action === 'CLOSE') return '关闭'
  if (action === 'LOGIN') return '登录'
  if (action === 'LOGOUT') return '退出'
  return '操作'
}

function mapModuleText(module: string) {
  if (module === 'SURVEY') return '问卷'
  if (module === 'USER') return '用户'
  if (module === 'PERMISSION') return '权限'
  return '系统'
}

function resolveTimelineType(action: string): DashboardTimelineType {
  if (action === 'CREATE') return 'create'
  if (action === 'PUBLISH') return 'publish'
  if (action === 'CLOSE') return 'close'
  return 'system'
}

function buildLogText(operator: string, action: string, module: string, target: string) {
  const safeOperator = operator || '管理员'
  const actionText = mapActionText(action)
  const moduleText = mapModuleText(module)
  const suffix = target ? ` ${target}` : ''
  return `${safeOperator}${actionText}了${moduleText}${suffix}`
}

function buildMockTimeline(): DashboardTimelineItem[] {
  const logStore = useLogStore()
  const rows = [...logStore.logList]
    .sort((a, b) => {
      const timeDiff = parseDateValue(b.createdAt) - parseDateValue(a.createdAt)
      return timeDiff !== 0 ? timeDiff : b.id - a.id
    })
    .slice(0, 6)

  if (rows.length === 0) {
    return [
      {
        id: 'system-default',
        time: '刚刚',
        text: '系统已就绪，等待新的问卷操作',
        type: 'system'
      }
    ]
  }

  return rows.map((item) => ({
    id: `log-${item.id}`,
    time: item.createdAt,
    text: buildLogText(item.operator, item.action, item.module, item.target),
    type: resolveTimelineType(item.action)
  }))
}

export async function getDashboard(): Promise<ApiResponse<DashboardResult>> {
  if (USE_REAL_API) {
    return request.get('/dashboard')
  }

  const surveyStore = useSurveyStore()
  const userStore = useUserStore()

  const surveyTotal = surveyStore.surveyList.length
  const publishedSurveyTotal = surveyStore.surveyList.filter(
    (item) => item.status === 'PUBLISHED'
  ).length
  const userTotal = userStore.userList.length
  const enabledUserTotal = userStore.userList.filter(
    (item) => item.status === 'ENABLED'
  ).length

  const recentSurveys = [...surveyStore.surveyList]
    .sort((a, b) => b.id - a.id)
    .slice(0, 6)

  const trend: DashboardTrendSeries = {
    today: {
      axis: buildTrendAxis('today'),
      values: buildTrendSeries('today', surveyTotal, publishedSurveyTotal, enabledUserTotal)
    },
    week: {
      axis: buildTrendAxis('week'),
      values: buildTrendSeries('week', surveyTotal, publishedSurveyTotal, enabledUserTotal)
    },
    month: {
      axis: buildTrendAxis('month'),
      values: buildTrendSeries('month', surveyTotal, publishedSurveyTotal, enabledUserTotal)
    }
  }

  return {
    code: 20000,
    message: 'success',
    data: {
      surveyTotal,
      publishedSurveyTotal,
      userTotal,
      enabledUserTotal,
      recentSurveys,
      trend,
      terminalStats: buildTerminalStats(surveyTotal, publishedSurveyTotal),
      sourceStats: buildSourceStats(surveyTotal, publishedSurveyTotal),
      hotSurveyRanking: buildMockRanking(recentSurveys),
      operationTimeline: buildMockTimeline()
    }
  }
}
