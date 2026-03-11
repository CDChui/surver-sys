<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import {
  exportSurveyStatsExcel,
  getSurveyStats,
  type QuestionSchemaItem,
  type SurveyStatsItem
} from '../../api/survey'
import { appendOperationLog } from '../../utils/log'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const exporting = ref(false)

const surveyId = computed(() => Number(route.params.id))

const surveyTitle = ref('')
const surveyDescription = ref('')
const schema = ref<QuestionSchemaItem[]>([])
const statsList = ref<SurveyStatsItem[]>([])
const responseCount = ref(0)

const chartRefs = ref<Record<number, HTMLDivElement | null>>({})
const chartInstances = ref<Record<number, echarts.ECharts>>({})

function setChartRef(id: number, el: HTMLDivElement | null) {
  chartRefs.value[id] = el
}

function getTypeText(type: string) {
  const map: Record<string, string> = {
    single: '单选题',
    multi: '多选题',
    text: '填空题',
    textarea: '简答题',
    rate: '评分题'
  }

  return map[type] || type
}

function isChartQuestion(item: SurveyStatsItem) {
  return item.type === 'single' || item.type === 'multi' || item.type === 'rate'
}

const cnNums = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']

function toChineseNumber(num: number) {
  if (!Number.isFinite(num) || num <= 0) return ''
  if (num < 10) return cnNums[num]
  if (num === 10) return '十'
  if (num < 20) return `十${cnNums[num % 10] || ''}`
  if (num < 100) {
    const tens = Math.floor(num / 10)
    const ones = num % 10
    return `${cnNums[tens]}十${ones ? cnNums[ones] : ''}`
  }
  if (num < 1000) {
    const hundreds = Math.floor(num / 100)
    const remainder = num % 100
    if (remainder === 0) return `${cnNums[hundreds]}百`
    if (remainder < 10) return `${cnNums[hundreds]}百零${cnNums[remainder]}`
    if (remainder < 20) {
      return `${cnNums[hundreds]}百一十${remainder % 10 ? cnNums[remainder % 10] : ''}`
    }
    return `${cnNums[hundreds]}百${toChineseNumber(remainder)}`
  }
  if (num < 10000) {
    const thousands = Math.floor(num / 1000)
    const remainder = num % 1000
    if (remainder === 0) return `${cnNums[thousands]}千`
    if (remainder < 10) return `${cnNums[thousands]}千零${cnNums[remainder]}`
    if (remainder < 20) {
      return `${cnNums[thousands]}千零一十${remainder % 10 ? cnNums[remainder % 10] : ''}`
    }
    if (remainder < 100) return `${cnNums[thousands]}千零${toChineseNumber(remainder)}`
    return `${cnNums[thousands]}千${toChineseNumber(remainder)}`
  }
  return String(num)
}

function formatQuestionIndex(index: number) {
  const no = toChineseNumber(index + 1)
  return no ? `第${no}题` : `第${index + 1}题`
}

function renderCharts() {
  Object.values(chartInstances.value).forEach((chart) => {
    chart.dispose()
  })
  chartInstances.value = {}

  statsList.value.forEach((item) => {
    if (!isChartQuestion(item)) return

    const el = chartRefs.value[item.id]
    if (!el) return

    const chart = echarts.init(el)
    chartInstances.value[item.id] = chart

    if (item.type === 'single') {
      chart.setOption({
        tooltip: {
          trigger: 'item'
        },
        legend: {
          bottom: 0
        },
        series: [
          {
            name: '单选统计',
            type: 'pie',
            radius: '60%',
            data: (item.optionStats || []).map((option) => ({
              name: option.label,
              value: option.count
            }))
          }
        ]
      })
      return
    }

    if (item.type === 'multi') {
      chart.setOption({
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: (item.optionStats || []).map((option) => option.label)
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            type: 'bar',
            data: (item.optionStats || []).map((option) => option.count)
          }
        ]
      })
      return
    }

    if (item.type === 'rate') {
      chart.setOption({
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: (item.rateStats || []).map((item) => `${item.score}分`)
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            type: 'bar',
            data: (item.rateStats || []).map((item) => item.count)
          }
        ]
      })
    }
  })
}

async function loadStats() {
  try {
    loading.value = true

    const response = await getSurveyStats(surveyId.value)

    if (response.code !== 20000) {
      alert(response.message || '加载统计页失败')
      return
    }

    surveyTitle.value = response.data.title
    surveyDescription.value = response.data.description
    schema.value = response.data.schema
    statsList.value = response.data.statsList
    responseCount.value = response.data.responseCount ?? 0

    await nextTick()
    renderCharts()
  } catch (error) {
    alert('加载统计页失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    exporting.value = true

    const response = await exportSurveyStatsExcel(surveyId.value)

    if (response.code !== 20000) {
      alert(response.message || '导出失败')
      return
    }

    appendOperationLog({
      module: 'SURVEY',
      action: 'UPDATE',
      target: `${surveyTitle.value || `问卷${surveyId.value}`} 统计导出`
    })

    alert('Excel 导出成功')
  } catch (error) {
    alert('导出失败')
  } finally {
    exporting.value = false
  }
}

function handleResponses() {
  router.push(`/admin/surveys/${surveyId.value}/responses`)
}

function handleBack() {
  router.push('/admin/surveys')
}

watch(
  () => statsList.value,
  async () => {
    await nextTick()
    renderCharts()
  }
)

onMounted(() => {
  void loadStats()
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  Object.values(chartInstances.value).forEach((chart) => {
    chart.resize()
  })
}
</script>

<template>
  <div>
    <el-card v-loading="loading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px;">
          <div>
            <h2 style="margin: 0;">问卷统计</h2>
            <p style="margin: 8px 0 0; color: #666;">
              当前问卷 ID：{{ surveyId }}
            </p>
          </div>

          <div style="display: flex; gap: 12px;">
            <el-button type="info" @click="handleResponses">
              答卷详情
            </el-button>

            <el-button type="success" :loading="exporting" @click="handleExport">
              导出 Excel
            </el-button>

            <el-button type="primary" @click="handleBack">
              返回问卷列表
            </el-button>
          </div>
        </div>
      </template>

      <div style="margin-bottom: 24px;">
        <h3 style="margin: 0 0 12px;">{{ surveyTitle }}</h3>
        <p style="margin: 0; color: #666; line-height: 1.8;">
          {{ surveyDescription }}
        </p>
      </div>

      <div
        style="
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
          gap: 16px;
          margin-bottom: 24px;
        "
      >
        <el-card>
          <div style="color: #666; margin-bottom: 8px;">题目总数</div>
          <div style="font-size: 28px; font-weight: 700;">{{ schema.length }}</div>
        </el-card>

        <el-card>
          <div style="color: #666; margin-bottom: 8px;">必答题数</div>
          <div style="font-size: 28px; font-weight: 700;">
            {{ schema.filter(item => item.required).length }}
          </div>
        </el-card>

        <el-card>
          <div style="color: #666; margin-bottom: 8px;">回收数</div>
          <div style="font-size: 28px; font-weight: 700;">{{ responseCount }}</div>
        </el-card>
      </div>

      <div>
        <div style="font-size: 18px; font-weight: 700; margin-bottom: 16px;">
          题目统计明细
        </div>

        <el-card
          v-for="(item, index) in statsList"
          :key="item.id"
          style="margin-bottom: 16px;"
        >
          <div style="margin-bottom: 12px;">
            <div style="font-weight: 700; margin-bottom: 6px;">
              {{ formatQuestionIndex(index) }} {{ item.title }}
            </div>
            <div style="color: #666;">
              类型：{{ getTypeText(item.type) }} ｜{{ item.required ? '必答' : '非必答' }}
            </div>
          </div>

          <div v-if="item.type === 'single' || item.type === 'multi' || item.type === 'rate'">
            <div
              :ref="(el) => setChartRef(item.id, el as HTMLDivElement | null)"
              style="width: 100%; height: 360px;"
            />
          </div>

          <div v-else-if="item.textSummary">
            <el-alert
              :title="item.textSummary"
              type="info"
              :closable="false"
              show-icon
            />
          </div>

          <div
            v-if="item.type === 'rate' && item.avgScore !== undefined"
            style="margin-top: 16px;"
          >
            <div style="font-size: 16px; color: #666; margin-bottom: 8px;">
              平均分
            </div>
            <div style="font-size: 28px; font-weight: 700; color: #409eff;">
              {{ item.avgScore }}
            </div>
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>
