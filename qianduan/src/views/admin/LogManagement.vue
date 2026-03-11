<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  getLogList,
  type LogAction,
  type LogItemResult,
  type LogModule,
  type LogType
} from '../../api/log'
import { useLogStore } from '../../stores/log'
import { useUserStore } from '../../stores/user'

type ModuleFilter = 'ALL' | LogModule
type ActionFilter = 'ALL' | LogAction
type SortOrder = 'ASC' | 'DESC'
type TableSortOrder = 'ascending' | 'descending' | null
type SortField =
  | 'id'
  | 'operator'
  | 'module'
  | 'action'
  | 'target'
  | 'createdAt'
  | 'terminalType'
  | 'sourceIp'

type LogRow = LogItemResult

const props = defineProps<{
  logType: LogType
}>()

const logStore = useLogStore()
const userStore = useUserStore()
const keyword = ref('')
const moduleFilter = ref<ModuleFilter>('ALL')
const actionFilter = ref<ActionFilter>('ALL')
const sortOrder = ref<SortOrder>('DESC')

const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const logList = ref<LogRow[]>([])
const sortState = ref<{ prop: SortField; order: TableSortOrder }>({
  prop: 'createdAt',
  order: 'descending'
})
let stopLogSubscribe: (() => void) | null = null

const filteredLogList = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return logList.value.filter((item) => {
    const matchKeyword =
      !text ||
      item.operator.toLowerCase().includes(text) ||
      item.target.toLowerCase().includes(text) ||
      String(item.sourceIp || '').toLowerCase().includes(text) ||
      String(item.terminalType || '').toLowerCase().includes(text)

    const matchModule =
      moduleFilter.value === 'ALL' || item.module === moduleFilter.value

    const matchAction =
      actionFilter.value === 'ALL' || item.action === actionFilter.value

    return matchKeyword && matchModule && matchAction
  })
})

const total = computed(() => filteredLogList.value.length)

function parseDateValue(value: string) {
  if (!value) return 0
  const parsed = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

function getSortValue(row: LogRow, prop: SortField) {
  if (prop === 'createdAt') {
    return parseDateValue(row.createdAt)
  }
  if (prop === 'id') {
    return row.id
  }
  return String(row[prop] ?? '')
}

function compareValues(a: number | string, b: number | string) {
  if (a === b) return 0
  return a > b ? 1 : -1
}

const sortedLogList = computed(() => {
  const { prop, order } = sortState.value
  if (!order) {
    return [...filteredLogList.value]
  }
  const factor = order === 'descending' ? -1 : 1
  return [...filteredLogList.value].sort((a, b) => {
    const left = getSortValue(a, prop)
    const right = getSortValue(b, prop)
    return compareValues(left, right) * factor
  })
})

const pagedLogList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedLogList.value.slice(start, end)
})

watch([keyword, moduleFilter, actionFilter], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

watch(
  () => props.logType,
  () => {
    currentPage.value = 1
    void loadLogList()
  }
)

watch(sortOrder, () => {
  sortState.value = {
    prop: 'createdAt',
    order: sortOrder.value === 'ASC' ? 'ascending' : 'descending'
  }
  currentPage.value = 1
  void loadLogList()
})

async function loadLogList() {
  try {
    loading.value = true

    const response = await getLogList({
      logType: props.logType,
      order: sortOrder.value
    })

    if (response.code !== 20000) {
      alert(response.message || '加载日志列表失败')
      return
    }

    logList.value = response.data
  } catch (error) {
    alert('加载日志列表失败')
  } finally {
    loading.value = false
  }
}

function getModuleText(module: LogModule) {
  if (module === 'SURVEY') return '问卷模块'
  if (module === 'USER') return '用户模块'
  if (module === 'PERMISSION') return '授权模块'
  return '系统模块'
}

function getActionText(action: LogAction) {
  if (action === 'CREATE') return '创建'
  if (action === 'UPDATE') return '修改'
  if (action === 'DELETE') return '删除'
  if (action === 'PUBLISH') return '发布'
  if (action === 'CLOSE') return '关闭'
  if (action === 'LOGIN') return '登录'
  return '退出'
}

function getActionTagType(action: LogAction) {
  if (action === 'CREATE' || action === 'PUBLISH' || action === 'LOGIN') return 'success'
  if (action === 'UPDATE') return 'primary'
  if (action === 'CLOSE' || action === 'LOGOUT') return 'warning'
  return 'danger'
}

function getOperatorText(operator: string) {
  const match = userStore.userList.find((item) => item.username === operator)
  return match?.realName || operator
}

function handleResetFilter() {
  keyword.value = ''
  moduleFilter.value = 'ALL'
  actionFilter.value = 'ALL'
  sortOrder.value = 'DESC'
}

function handlePageChange(page: number) {
  currentPage.value = page
}

function handleSizeChange(size: number) {
  pageSize.value = size
}

function handleSortChange({ prop, order }: { prop: SortField; order: TableSortOrder }) {
  if (!prop || !order) {
    sortState.value = { prop: 'createdAt', order: 'descending' }
    if (sortOrder.value !== 'DESC') {
      sortOrder.value = 'DESC'
    }
    currentPage.value = 1
    return
  }

  sortState.value = { prop, order }
  if (prop === 'createdAt') {
    const nextOrder = order === 'ascending' ? 'ASC' : 'DESC'
    if (sortOrder.value !== nextOrder) {
      sortOrder.value = nextOrder
    }
  }
  currentPage.value = 1
}

function handleStorageChange(event: StorageEvent) {
  if (event.key === 'SYSTEM_LOG_LIST') {
    void loadLogList()
  }
}

function startAutoRefresh() {
  stopLogSubscribe = logStore.$subscribe(() => {
    void loadLogList()
  })
}

onMounted(() => {
  void loadLogList()
  startAutoRefresh()
  window.addEventListener('storage', handleStorageChange)
})

onUnmounted(() => {
  stopLogSubscribe?.()
  stopLogSubscribe = null
  window.removeEventListener('storage', handleStorageChange)
})
</script>

<template>
  <div>
    <el-card style="margin-bottom: 16px;">
      <div
        style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"
      >
        <el-input
          v-model="keyword"
          placeholder="请输入操作人、目标对象或IP地址"
          clearable
          style="max-width: 280px;"
        />

        <el-select
          v-model="moduleFilter"
          placeholder="请选择模块"
          style="width: 180px;"
        >
          <el-option label="全部模块" value="ALL" />
          <el-option label="问卷模块" value="SURVEY" />
          <el-option label="用户模块" value="USER" />
          <el-option label="授权模块" value="PERMISSION" />
          <el-option label="系统模块" value="SYSTEM" />
        </el-select>

        <el-select
          v-model="actionFilter"
          placeholder="请选择操作类型"
          style="width: 180px;"
        >
          <el-option label="全部操作" value="ALL" />
          <el-option label="创建" value="CREATE" />
          <el-option label="修改" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="发布" value="PUBLISH" />
          <el-option label="关闭" value="CLOSE" />
          <el-option label="登录" value="LOGIN" />
          <el-option label="退出" value="LOGOUT" />
        </el-select>

        <el-select
          v-model="sortOrder"
          placeholder="排序方式"
          style="width: 160px;"
        >
          <el-option label="时间降序" value="DESC" />
          <el-option label="时间升序" value="ASC" />
        </el-select>

        <el-button @click="handleResetFilter">
          重置筛选
        </el-button>

        <span style="color: #666;">
          当前结果：{{ total }} 条
        </span>
      </div>
    </el-card>

    <el-card v-loading="loading">
      <el-table
        :data="pagedLogList"
        style="width: 100%"
        :default-sort="{ prop: 'createdAt', order: 'descending' }"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="id" label="ID" width="90" sortable="custom" />
        <el-table-column prop="operator" label="操作人" width="140" sortable="custom">
          <template #default="scope">
            {{ getOperatorText(scope.row.operator) }}
          </template>
        </el-table-column>
        <el-table-column prop="terminalType" label="终端类型" width="120" sortable="custom">
          <template #default="scope">
            {{ scope.row.terminalType || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceIp" label="IP地址" width="150" sortable="custom">
          <template #default="scope">
            {{ scope.row.sourceIp || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="140" sortable="custom">
          <template #default="scope">
            {{ getModuleText(scope.row.module) }}
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作类型" width="120" sortable="custom">
          <template #default="scope">
            <el-tag :type="getActionTagType(scope.row.action)">
              {{ getActionText(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="目标对象" min-width="220" sortable="custom" />
        <el-table-column prop="createdAt" label="操作时间" width="180" sortable="custom" />
      </el-table>

      <div
        v-if="pagedLogList.length === 0"
        style="padding: 24px 0 8px; text-align: center; color: #999;"
      >
        没有找到符合条件的日志
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
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </el-config-provider>
      </div>
    </el-card>
  </div>
</template>




