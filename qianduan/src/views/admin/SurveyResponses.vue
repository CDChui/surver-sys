<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  getSurveyResponses,
  type QuestionSchemaItem,
  type SurveyResponseItemResult
} from '../../api/survey'

type SortOrder = 'ascending' | 'descending' | null
type SortField =
  | 'submitTime'
  | 'account'
  | 'username'
  | 'terminalType'
  | 'sourceType'
  | 'sourceIp'

type ResponseRow = SurveyResponseItemResult

const route = useRoute()
const router = useRouter()

const surveyId = computed(() => Number(route.params.id))

const surveyTitle = ref('')
const surveyDescription = ref('')
const schema = ref<QuestionSchemaItem[]>([])

const responseList = ref<ResponseRow[]>([])
const keyword = ref('')
const loading = ref(false)

const currentPage = ref(1)
const pageSize = ref(20)

const sortState = ref<{ prop: SortField; order: SortOrder }>({
  prop: 'submitTime',
  order: 'descending'
})

const filteredResponses = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return responseList.value

  return responseList.value.filter((item) => {
    const account = String(item.account || '').toLowerCase()
    const username = String(item.username || '').toLowerCase()
    const userId = String(item.userId || '')
    const sourceIp = String(item.sourceIp || '').toLowerCase()
    const terminalType = String(item.terminalType || '').toLowerCase()
    const sourceType = String(item.sourceType || '').toLowerCase()
    return (
      account.includes(text) ||
      username.includes(text) ||
      userId.includes(text) ||
      sourceIp.includes(text) ||
      terminalType.includes(text) ||
      sourceType.includes(text)
    )
  })
})

const total = computed(() => filteredResponses.value.length)

function parseDateValue(value: string) {
  if (!value) return 0
  const parsed = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

function getSortValue(row: ResponseRow, prop: SortField) {
  if (prop === 'submitTime') {
    return parseDateValue(row.submitTime)
  }
  return String(row[prop] ?? '')
}

function compareValues(a: number | string, b: number | string) {
  if (a === b) return 0
  return a > b ? 1 : -1
}

const sortedResponses = computed(() => {
  const { prop, order } = sortState.value
  if (!order) {
    return [...filteredResponses.value]
  }
  const factor = order === 'descending' ? -1 : 1
  return [...filteredResponses.value].sort((a, b) => {
    const left = getSortValue(a, prop)
    const right = getSortValue(b, prop)
    return compareValues(left, right) * factor
  })
})

const pagedResponses = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedResponses.value.slice(start, end)
})

watch(keyword, () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

function formatAnswerValue(value: unknown, emptyText = '未填写') {
  if (Array.isArray(value)) {
    const text = value.map((item) => String(item)).join('、')
    return text || emptyText
  }
  if (value === undefined || value === null || value === '') {
    return emptyText
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch (error) {
      return String(value)
    }
  }
  return String(value)
}

async function loadResponses() {
  if (!surveyId.value) {
    alert('问卷参数错误')
    router.push('/admin/surveys')
    return
  }

  try {
    loading.value = true
    const response = await getSurveyResponses(surveyId.value)

    if (response.code !== 20000) {
      alert(response.message || '加载答卷详情失败')
      return
    }

    surveyTitle.value = response.data.surveyTitle || `问卷${surveyId.value}`
    surveyDescription.value = response.data.surveyDescription || ''
    schema.value = response.data.schema || []
    responseList.value = response.data.responses || []
  } catch (error) {
    alert('加载答卷详情失败')
  } finally {
    loading.value = false
  }
}

function handleSortChange({ prop, order }: { prop: SortField; order: SortOrder }) {
  if (!prop || !order) {
    sortState.value = { prop: 'submitTime', order: 'descending' }
  } else {
    sortState.value = { prop, order }
  }
  currentPage.value = 1
}

function handleResetSearch() {
  keyword.value = ''
}

function handleBackToStats() {
  router.push(`/admin/surveys/${surveyId.value}/stats`)
}

function handleBackToList() {
  router.push('/admin/surveys')
}

onMounted(() => {
  void loadResponses()
})
</script>

<template>
  <div>
    <el-card style="margin-bottom: 16px;">
      <div
        style="
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;
        "
      >
        <div>
          <div style="display: flex; align-items: baseline; gap: 12px;">
            <h2 style="margin: 0;">答卷详情</h2>
            <span style="color: #666;">问卷 ID：{{ surveyId }}</span>
          </div>
          <p style="margin: 8px 0 0; color: #666;">
            问卷标题：{{ surveyTitle || `问卷${surveyId}` }}
          </p>
        </div>

        <div style="display: flex; gap: 12px;">
          <el-button type="info" @click="handleBackToStats">返回统计</el-button>
          <el-button type="primary" @click="handleBackToList">返回问卷列表</el-button>
        </div>
      </div>

      <p v-if="surveyDescription" style="margin: 12px 0 0; color: #666;">
        {{ surveyDescription }}
      </p>
    </el-card>

    <el-card style="margin-bottom: 16px;">
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
        <el-input
          v-model="keyword"
          placeholder="请输入账号、用户名、来源 IP、终端类型或来源"
          clearable
          style="max-width: 320px;"
        />

        <el-button @click="handleResetSearch">重置搜索</el-button>

        <span style="color: #666;">当前结果：{{ total }} 条</span>
      </div>
    </el-card>

    <el-card v-loading="loading">
      <el-table
        :data="pagedResponses"
        style="width: 100%"
        :default-sort="{ prop: 'submitTime', order: 'descending' }"
        @sort-change="handleSortChange"
      >
        <el-table-column type="expand" width="40">
          <template #default="scope">
            <div style="padding: 8px 16px;">
              <div v-if="schema.length === 0" style="color: #999;">
                暂无题目结构
              </div>
              <div
                v-for="(question, index) in schema"
                :key="question.id"
                style="margin-bottom: 12px;"
              >
                <div style="font-weight: 600;">
                  {{ index + 1 }}. {{ question.title }}
                </div>
                <div style="margin-top: 6px; color: #333;">
                  {{ formatAnswerValue(scope.row.answers[String(question.id)]) }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="submitTime"
          label="提交时间"
          width="180"
          sortable="custom"
        />
        <el-table-column prop="account" label="账号" width="160" sortable="custom" />
        <el-table-column prop="username" label="用户名" width="160" sortable="custom" />
        <el-table-column prop="terminalType" label="终端类型" width="120" sortable="custom">
          <template #default="scope">
            {{ scope.row.terminalType || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="120" sortable="custom">
          <template #default="scope">
            {{ scope.row.sourceType || '直接链接' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceIp" label="来源 IP" width="160" sortable="custom">
          <template #default="scope">
            {{ scope.row.sourceIp || '-' }}
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="pagedResponses.length === 0"
        style="padding: 24px 0 8px; text-align: center; color: #999;"
      >
        没有找到符合条件的答卷
      </div>

      <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
        <el-config-provider :locale="zhCn">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="(page) => (currentPage = page)"
            @size-change="(size) => (pageSize = size)"
          />
        </el-config-provider>
      </div>
    </el-card>
  </div>
</template>
