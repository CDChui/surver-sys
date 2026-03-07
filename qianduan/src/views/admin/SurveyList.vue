<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getSurveyList, type SurveyListItemResult } from '../../api/survey'
import { useSurveyStore } from '../../stores/survey'

const router = useRouter()
const surveyStore = useSurveyStore()

type SurveyStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
type StatusFilter = 'ALL' | SurveyStatus

type SurveyItem = SurveyListItemResult

const keyword = ref('')
const statusFilter = ref<StatusFilter>('ALL')

const currentPage = ref(1)
const pageSize = ref(5)
const loading = ref(false)

const surveyList = ref<SurveyItem[]>([])

const filteredSurveyList = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return surveyList.value.filter((item) => {
    const matchKeyword = !text || item.title.toLowerCase().includes(text)
    const matchStatus =
      statusFilter.value === 'ALL' || item.status === statusFilter.value

    return matchKeyword && matchStatus
  })
})

const total = computed(() => filteredSurveyList.value.length)

const pagedSurveyList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredSurveyList.value.slice(start, end)
})

watch([keyword, statusFilter], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

async function loadSurveyList() {
  try {
    loading.value = true
    const response = await getSurveyList()

    if (response.code === 40301) {
      alert(response.message || '无权访问问卷列表')
      router.push('/m')
      return
    }

    if (response.code !== 20000) {
      alert(response.message || '加载问卷列表失败')
      return
    }

    surveyList.value = response.data
  } catch (error) {
    alert('加载问卷列表失败')
  } finally {
    loading.value = false
  }
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

function handleCreate() {
  router.push('/admin/surveys/new')
}

function handleEdit(row: SurveyItem) {
  router.push(`/admin/surveys/${row.id}/edit`)
}

function handleAuth(row: SurveyItem) {
  router.push(`/admin/surveys/${row.id}/auth`)
}

function handleOpenMobile(row: SurveyItem) {
  router.push(`/m/surveys/${row.id}`)
}

function handleStats(row: SurveyItem) {
  router.push(`/admin/surveys/${row.id}/stats`)
}

function handlePublish(row: SurveyItem) {
  if (row.status === 'PUBLISHED') {
    alert('该问卷已经是已发布状态')
    return
  }

  surveyStore.publishSurvey(row.id)
  void loadSurveyList()
  alert(`已发布：${row.title}`)
}

function handleClose(row: SurveyItem) {
  if (row.status === 'CLOSED') {
    alert('该问卷已经是已关闭状态')
    return
  }

  surveyStore.closeSurvey(row.id)
  void loadSurveyList()
  alert(`已关闭：${row.title}`)
}

function handleDelete(row: SurveyItem) {
  const ok = window.confirm(`确定删除《${row.title}》吗？`)
  if (!ok) return

  surveyStore.deleteSurvey(row.id)
  void loadSurveyList()
  alert(`已删除：${row.title}`)
}

function handleClearSearch() {
  keyword.value = ''
}

function handleResetFilter() {
  keyword.value = ''
  statusFilter.value = 'ALL'
}

function handlePageChange(page: number) {
  currentPage.value = page
}

function handleSizeChange(size: number) {
  pageSize.value = size
}

onMounted(() => {
  void loadSurveyList()
})
</script>

<template>
  <div>
    <el-card style="margin-bottom: 16px;">
      <div
        style="display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap;"
      >
        <div
          style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"
        >
          <el-input
            v-model="keyword"
            placeholder="请输入问卷标题关键词"
            clearable
            style="max-width: 320px;"
          />

          <el-select
            v-model="statusFilter"
            placeholder="请选择状态"
            style="width: 180px;"
          >
            <el-option label="全部状态" value="ALL" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>

          <el-button @click="handleClearSearch">
            清空搜索
          </el-button>

          <el-button @click="handleResetFilter">
            重置筛选
          </el-button>
        </div>

        <div>
          <el-button type="primary" @click="handleCreate">
            创建问卷
          </el-button>
        </div>
      </div>

      <div style="margin-top: 12px; color: #666;">
        当前结果：{{ total }} 条
      </div>
    </el-card>

    <el-card v-loading="loading">
      <el-table :data="pagedSurveyList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="title" label="问卷标题" min-width="220" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="600">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">
              编辑
            </el-button>

            <el-button size="small" @click="handleAuth(scope.row)">
              授权
            </el-button>

            <el-button size="small" type="primary" @click="handleOpenMobile(scope.row)">
              访问问卷
            </el-button>

            <el-button size="small" type="success" @click="handlePublish(scope.row)">
              发布
            </el-button>

            <el-button size="small" type="warning" @click="handleClose(scope.row)">
              关闭
            </el-button>

            <el-button size="small" type="info" @click="handleStats(scope.row)">
              统计
            </el-button>

            <el-button size="small" type="danger" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="pagedSurveyList.length === 0"
        style="padding: 24px 0 8px; text-align: center; color: #999;"
      >
        没有找到符合条件的问卷
      </div>

      <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          :page-sizes="[5, 10, 20]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>