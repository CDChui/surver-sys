<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useSurveyStore } from '../../stores/survey'
import { useUserStore } from '../../stores/user'

type SurveyStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
type TrendRange = 'today' | 'week' | 'month'
type TimelineType = 'create' | 'publish' | 'close' | 'system'

interface RankItem {
  id: number
  title: string
  count: number
  percent: number
  rank: number
}

interface TimelineItem {
  id: string
  time: string
  text: string
  type: TimelineType
}

const surveyStore = useSurveyStore()
const userStore = useUserStore()

const surveyTotal = computed(() => surveyStore.surveyList.length)
const publishedSurveyTotal = computed(() => {
  return surveyStore.surveyList.filter((item) => item.status === 'PUBLISHED').length
})
const userTotal = computed(() => userStore.userList.length)
const enabledUserTotal = computed(() => {
  return userStore.userList.filter((item) => item.status === 'ENABLED').length
})

const recentSurveys = computed(() => {
  return [...surveyStore.surveyList].sort((a, b) => b.id - a.id).slice(0, 6)
})

const trendRange = ref<TrendRange>('week')
const trendRangeOptions: Array<{ label: string; value: TrendRange }> = [
  { label: '今日', value: 'today' },
  { label: '本周', value: 'week' },
  { label: '本月', value: 'month' }
]

const trendChartRef = ref<HTMLDivElement | null>(null)
const terminalChartRef = ref<HTMLDivElement | null>(null)
const sourceChartRef = ref<HTMLDivElement | null>(null)

let trendChart: echarts.ECharts | null = null
let terminalChart: echarts.ECharts | null = null
let sourceChart: echarts.ECharts | null = null

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function buildTrendAxis(range: TrendRange) {
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

function buildTrendSeries(range: TrendRange) {
  const axis = buildTrendAxis(range)
  const base = Math.max(
    16,
    surveyTotal.value * 9 + publishedSurveyTotal.value * 15 + enabledUserTotal.value * 5
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

function renderTrendChart() {
  if (!trendChartRef.value) return

  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const axisData = buildTrendAxis(trendRange.value)
  const seriesData = buildTrendSeries(trendRange.value)
  const isToday = trendRange.value === 'today'

  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: {
        color: '#fff'
      }
    },
    grid: {
      left: 36,
      right: 18,
      top: 24,
      bottom: 34
    },
    xAxis: {
      type: 'category',
      boundaryGap: isToday,
      data: axisData,
      axisLine: {
        lineStyle: {
          color: '#d1d5db'
        }
      },
      axisLabel: {
        color: '#6b7280'
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: {
        show: false
      },
      splitLine: {
        lineStyle: {
          color: '#eef2f7'
        }
      },
      axisLabel: {
        color: '#6b7280'
      }
    },
    series: [
      isToday
        ? {
            name: '新增答卷',
            type: 'bar',
            barWidth: 16,
            data: seriesData,
            itemStyle: {
              borderRadius: [4, 4, 0, 0],
              color: '#2563eb'
            }
          }
        : {
            name: '新增答卷',
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 7,
            data: seriesData,
            lineStyle: {
              width: 3,
              color: '#1d4ed8'
            },
            itemStyle: {
              color: '#1d4ed8'
            },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(29, 78, 216, 0.32)' },
                { offset: 1, color: 'rgba(29, 78, 216, 0.03)' }
              ])
            }
          }
    ]
  })
}

function getTerminalData() {
  const mobile = Math.min(86, Math.max(52, 58 + publishedSurveyTotal.value * 4 - surveyTotal.value))
  return [
    { name: '手机端', value: mobile },
    { name: '电脑端', value: 100 - mobile }
  ]
}

function getSourceData() {
  const raw = [
    { name: '微信分享', value: 45 + publishedSurveyTotal.value * 3 },
    { name: '直接链接访问', value: 34 + surveyTotal.value * 2 },
    { name: '二维码扫描', value: 26 + enabledUserTotal.value * 2 }
  ]
  return raw
}

function renderTerminalChart() {
  if (!terminalChartRef.value) return

  if (!terminalChart) {
    terminalChart = echarts.init(terminalChartRef.value)
  }

  terminalChart.setOption({
    tooltip: {
      trigger: 'item'
    },
    legend: {
      bottom: 0,
      textStyle: {
        color: '#4b5563'
      }
    },
    series: [
      {
        name: '终端占比',
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '42%'],
        label: {
          formatter: '{b}\n{d}%'
        },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 3
        },
        color: ['#1d4ed8', '#60a5fa'],
        data: getTerminalData()
      }
    ]
  })
}

function renderSourceChart() {
  if (!sourceChartRef.value) return

  if (!sourceChart) {
    sourceChart = echarts.init(sourceChartRef.value)
  }

  sourceChart.setOption({
    tooltip: {
      trigger: 'item'
    },
    legend: {
      bottom: 0,
      textStyle: {
        color: '#4b5563'
      }
    },
    series: [
      {
        name: '来源占比',
        type: 'pie',
        roseType: 'radius',
        radius: ['24%', '74%'],
        center: ['50%', '42%'],
        label: {
          formatter: '{b}\n{d}%'
        },
        color: ['#123b93', '#2f71e8', '#22d3ee'],
        data: getSourceData()
      }
    ]
  })
}

function renderAllCharts() {
  renderTrendChart()
  renderTerminalChart()
  renderSourceChart()
}

function getHeatCount(status: SurveyStatus, id: number, index: number) {
  const tail = Number(String(id).slice(-3)) || (index + 1) * 17
  const statusBoost = status === 'PUBLISHED' ? 240 : status === 'CLOSED' ? 140 : 95
  return statusBoost + (tail % 180)
}

const hotSurveyRanking = computed<RankItem[]>(() => {
  const rows = surveyStore.surveyList
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
})

const operationTimeline = computed<TimelineItem[]>(() => {
  const events: TimelineItem[] = []
  const topSurveys = [...surveyStore.surveyList].sort((a, b) => b.id - a.id).slice(0, 6)

  topSurveys.forEach((survey) => {
    const time = survey.createdAt || '刚刚'

    events.push({
      id: `create-${survey.id}`,
      time,
      text: `管理员创建了《${survey.title}》`,
      type: 'create'
    })

    if (survey.status === 'PUBLISHED') {
      events.push({
        id: `publish-${survey.id}`,
        time,
        text: `《${survey.title}》已发布并开始回收答卷`,
        type: 'publish'
      })
    }

    if (survey.status === 'CLOSED') {
      events.push({
        id: `close-${survey.id}`,
        time,
        text: `《${survey.title}》已收集满额并自动结束`,
        type: 'close'
      })
    }
  })

  if (events.length === 0) {
    events.push({
      id: 'system-default',
      time: '刚刚',
      text: '系统已就绪，等待新的问卷操作动态',
      type: 'system'
    })
  }

  return events.slice(0, 6)
})

function getStatusText(status: SurveyStatus) {
  if (status === 'DRAFT') return '草稿'
  if (status === 'PUBLISHED') return '已发布'
  return '已关闭'
}

function getStatusType(status: SurveyStatus) {
  if (status === 'DRAFT') return 'info'
  if (status === 'PUBLISHED') return 'success'
  return 'danger'
}

function getRankBarColor(rank: number) {
  if (rank === 1) return '#1d4ed8'
  if (rank === 2) return '#2563eb'
  if (rank === 3) return '#3b82f6'
  if (rank === 4) return '#60a5fa'
  return '#93c5fd'
}

function getTimelineColor(type: TimelineType) {
  if (type === 'create') return '#3b82f6'
  if (type === 'publish') return '#22c55e'
  if (type === 'close') return '#f59e0b'
  return '#9ca3af'
}

function handleResize() {
  trendChart?.resize()
  terminalChart?.resize()
  sourceChart?.resize()
}

watch(
  [trendRange, surveyTotal, publishedSurveyTotal, enabledUserTotal],
  async () => {
    await nextTick()
    renderAllCharts()
  },
  { deep: true }
)

onMounted(async () => {
  await nextTick()
  renderAllCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)

  trendChart?.dispose()
  terminalChart?.dispose()
  sourceChart?.dispose()

  trendChart = null
  terminalChart = null
  sourceChart = null
})
</script>

<template>
  <div>
    <div
      style="
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      "
    >
      <el-card>
        <div style="color: #666; margin-bottom: 8px;">问卷总数</div>
        <div style="font-size: 30px; font-weight: 700;">{{ surveyTotal }}</div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">已发布问卷</div>
        <div style="font-size: 30px; font-weight: 700; color: #67c23a;">
          {{ publishedSurveyTotal }}
        </div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">用户总数</div>
        <div style="font-size: 30px; font-weight: 700;">{{ userTotal }}</div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">启用用户数</div>
        <div style="font-size: 30px; font-weight: 700; color: #409eff;">
          {{ enabledUserTotal }}
        </div>
      </el-card>
    </div>

    <div
      style="
        display: grid;
        grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
        gap: 16px;
        align-items: start;
        margin-bottom: 16px;
      "
    >
      <el-card>
        <template #header>
          <div
            style="
              display: flex;
              justify-content: space-between;
              align-items: center;
              gap: 12px;
              flex-wrap: wrap;
            "
          >
            <div style="font-weight: 700;">答卷收集趋势</div>
            <el-radio-group v-model="trendRange" size="small">
              <el-radio-button
                v-for="item in trendRangeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <div ref="trendChartRef" style="height: 340px;" />
      </el-card>

      <el-card>
        <template #header>
          <div style="font-weight: 700;">访问终端与来源分析</div>
        </template>

        <div style="display: grid; gap: 12px;">
          <div>
            <div style="font-size: 14px; color: #666; margin-bottom: 6px;">
              终端占比：手机端 vs 电脑端
            </div>
            <div ref="terminalChartRef" style="height: 190px;" />
          </div>

          <div>
            <div style="font-size: 14px; color: #666; margin-bottom: 6px;">
              来源占比：微信 / 直接链接 / 二维码
            </div>
            <div ref="sourceChartRef" style="height: 190px;" />
          </div>
        </div>
      </el-card>
    </div>

    <div
      style="
        display: grid;
        grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
        gap: 16px;
        align-items: start;
      "
    >
      <el-card>
        <template #header>
          <div style="font-weight: 700;">最近问卷</div>
        </template>

        <el-table :data="recentSurveys" style="width: 100%">
          <el-table-column prop="id" label="ID" width="110" />
          <el-table-column prop="title" label="问卷标题" min-width="220" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
      </el-card>

      <div style="display: flex; flex-direction: column; gap: 16px;">
        <el-card>
          <template #header>
            <div style="font-weight: 700;">热门问卷排行（Top 5）</div>
          </template>

          <div v-if="hotSurveyRanking.length > 0">
            <div
              v-for="item in hotSurveyRanking"
              :key="item.id"
              style="margin-bottom: 12px;"
            >
              <div
                style="
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  margin-bottom: 6px;
                  gap: 10px;
                "
              >
                <div
                  style="
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    min-width: 0;
                    overflow: hidden;
                  "
                >
                  <span
                    style="
                      width: 20px;
                      height: 20px;
                      border-radius: 50%;
                      background: #eff6ff;
                      color: #1d4ed8;
                      font-size: 12px;
                      font-weight: 700;
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      flex-shrink: 0;
                    "
                  >
                    {{ item.rank }}
                  </span>
                  <span
                    style="
                      color: #334155;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    "
                  >
                    {{ item.title }}
                  </span>
                </div>

                <span style="font-weight: 700; color: #1e3a8a;">{{ item.count }}</span>
              </div>
              <el-progress
                :percentage="item.percent"
                :stroke-width="6"
                :show-text="false"
                :color="getRankBarColor(item.rank)"
              />
            </div>
          </div>

          <div v-else style="color: #999;">暂无热门问卷数据</div>
        </el-card>

        <el-card>
          <template #header>
            <div style="font-weight: 700;">团队操作动态</div>
          </template>

          <el-timeline>
            <el-timeline-item
              v-for="item in operationTimeline"
              :key="item.id"
              :timestamp="item.time"
              :color="getTimelineColor(item.type)"
            >
              {{ item.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </div>
    </div>
  </div>
</template>
