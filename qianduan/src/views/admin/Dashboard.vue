<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  getDashboard,
  type DashboardDistributionItem,
  type DashboardResult,
  type DashboardTimelineItem,
  type DashboardTrendRange
} from '../../api/dashboard'

type SurveyStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
type TimelineType = 'create' | 'publish' | 'close' | 'system'

const loading = ref(false)
const dashboard = ref<DashboardResult | null>(null)

const trendRange = ref<DashboardTrendRange>('week')
const trendRangeOptions: Array<{ label: string; value: DashboardTrendRange }> = [
  { label: '今日', value: 'today' },
  { label: '近7天', value: 'week' },
  { label: '近30天', value: 'month' }
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

const defaultTrend = {
  today: {
    axis: buildTrendAxis('today'),
    values: Array.from({ length: 12 }, () => 0)
  },
  week: {
    axis: buildTrendAxis('week'),
    values: Array.from({ length: 7 }, () => 0)
  },
  month: {
    axis: buildTrendAxis('month'),
    values: Array.from({ length: 30 }, () => 0)
  }
}

const defaultTerminalStats: DashboardDistributionItem[] = [
  { name: '移动端', value: 0 },
  { name: 'PC', value: 0 },
  { name: '平板', value: 0 },
  { name: '鸿蒙', value: 0 }
]

const defaultSourceStats: DashboardDistributionItem[] = [
  { name: '微信', value: 0 },
  { name: '直接链接', value: 0 }
]

const defaultTimeline: DashboardTimelineItem[] = [
  {
    id: 'system-default',
    time: '刚刚',
    text: '系统已就绪，等待新的问卷操作',
    type: 'system'
  }
]

const surveyTotal = computed(() => dashboard.value?.surveyTotal ?? 0)
const publishedSurveyTotal = computed(() => dashboard.value?.publishedSurveyTotal ?? 0)
const userTotal = computed(() => dashboard.value?.userTotal ?? 0)
const enabledUserTotal = computed(() => dashboard.value?.enabledUserTotal ?? 0)
const recentSurveys = computed(() => dashboard.value?.recentSurveys ?? [])
const trendData = computed(() => dashboard.value?.trend ?? defaultTrend)
const terminalStats = computed(() => {
  const data = dashboard.value?.terminalStats ?? []
  return data.length > 0 ? data : defaultTerminalStats
})
const sourceStats = computed(() => {
  const data = dashboard.value?.sourceStats ?? []
  return data.length > 0 ? data : defaultSourceStats
})
const hotSurveyRanking = computed(() => dashboard.value?.hotSurveyRanking ?? [])
const operationTimeline = computed(() => {
  const list = dashboard.value?.operationTimeline ?? []
  return list.length > 0 ? list : defaultTimeline
})

function renderTrendChart() {
  if (!trendChartRef.value) return

  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const rangeData = trendData.value[trendRange.value] || defaultTrend.week
  const axisData = rangeData.axis
  const seriesData = rangeData.values
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
        color: ['#1d4ed8', '#60a5fa', '#93c5fd', '#22d3ee'],
        data: terminalStats.value
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
        color: ['#123b93', '#2f71e8'],
        data: sourceStats.value
      }
    ]
  })
}

function renderAllCharts() {
  renderTrendChart()
  renderTerminalChart()
  renderSourceChart()
}

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

async function loadDashboard() {
  try {
    loading.value = true
    const response = await getDashboard()

    if (response.code !== 20000) {
      alert(response.message || '加载数据看板失败')
      return
    }

    dashboard.value = response.data
  } catch (error) {
    alert('加载数据看板失败')
  } finally {
    loading.value = false
    await nextTick()
    renderAllCharts()
  }
}

watch([trendRange, dashboard], async () => {
  await nextTick()
  renderAllCharts()
})

onMounted(async () => {
  await loadDashboard()
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
  <div v-loading="loading">
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
          <div style="font-weight: 700;">访问终端与来源分布</div>
        </template>

        <div style="display: grid; gap: 12px;">
          <div>
            <div style="font-size: 14px; color: #666; margin-bottom: 6px;">
              终端占比：移动端 / PC / 平板 / 鸿蒙
            </div>
            <div ref="terminalChartRef" style="height: 190px;" />
          </div>

          <div>
            <div style="font-size: 14px; color: #666; margin-bottom: 6px;">
              来源占比：微信 / 直接链接
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
            <div style="font-weight: 700;">热门问卷排行（前 5）</div>
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
