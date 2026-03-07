<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getLogList, type LogAction, type LogItemResult, type LogModule } from '../../api/log'

type ModuleFilter = 'ALL' | LogModule
type ActionFilter = 'ALL' | LogAction

type LogRow = LogItemResult

const keyword = ref('')
const moduleFilter = ref<ModuleFilter>('ALL')
const actionFilter = ref<ActionFilter>('ALL')

const currentPage = ref(1)
const pageSize = ref(5)
const loading = ref(false)

const logList = ref<LogRow[]>([])

const filteredLogList = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return logList.value.filter((item) => {
    const matchKeyword =
      !text ||
      item.operator.toLowerCase().includes(text) ||
      item.target.toLowerCase().includes(text)

    const matchModule =
      moduleFilter.value === 'ALL' || item.module === moduleFilter.value

    const matchAction =
      actionFilter.value === 'ALL' || item.action === actionFilter.value

    return matchKeyword && matchModule && matchAction
  })
})

const total = computed(() => filteredLogList.value.length)

const pagedLogList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredLogList.value.slice(start, end)
})

watch([keyword, moduleFilter, actionFilter], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

async function loadLogList() {
  try {
    loading.value = true

    const response = await getLogList()

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

function handleResetFilter() {
  keyword.value = ''
  moduleFilter.value = 'ALL'
  actionFilter.value = 'ALL'
}

function handlePageChange(page: number) {
  currentPage.value = page
}

function handleSizeChange(size: number) {
  pageSize.value = size
}

onMounted(() => {
  void loadLogList()
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
          placeholder="请输入操作人或目标对象"
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

        <el-button @click="handleResetFilter">
          重置筛选
        </el-button>

        <span style="color: #666;">
          当前结果：{{ total }} 条
        </span>
      </div>
    </el-card>

    <el-card v-loading="loading">
      <el-table :data="pagedLogList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="operator" label="操作人" width="140" />
        <el-table-column prop="module" label="模块" width="140">
          <template #default="scope">
            {{ getModuleText(scope.row.module) }}
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作类型" width="120">
          <template #default="scope">
            <el-tag :type="getActionTagType(scope.row.action)">
              {{ getActionText(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="目标对象" min-width="220" />
        <el-table-column prop="createdAt" label="操作时间" width="180" />
      </el-table>

      <div
        v-if="pagedLogList.length === 0"
        style="padding: 24px 0 8px; text-align: center; color: #999;"
      >
        没有找到符合条件的日志
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