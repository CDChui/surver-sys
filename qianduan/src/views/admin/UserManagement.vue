<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  deleteUserApi,
  getUserList,
  updateUserStatus,
  type UserItemResult,
  type UserRole,
  type UserStatus
} from '../../api/user'
import { useUserStore } from '../../stores/user'

type RoleFilter = 'ALL' | UserRole
type StatusFilter = 'ALL' | UserStatus

type UserRow = UserItemResult

const userStore = useUserStore()

const keyword = ref('')
const roleFilter = ref<RoleFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')

const currentPage = ref(1)
const pageSize = ref(5)
const loading = ref(false)

const userList = ref<UserRow[]>([])

const filteredUserList = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return userList.value.filter((item) => {
    const matchKeyword =
      !text ||
      item.username.toLowerCase().includes(text) ||
      item.realName.toLowerCase().includes(text)

    const matchRole =
      roleFilter.value === 'ALL' || item.role === roleFilter.value

    const matchStatus =
      statusFilter.value === 'ALL' || item.status === statusFilter.value

    return matchKeyword && matchRole && matchStatus
  })
})

const total = computed(() => filteredUserList.value.length)

const pagedUserList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredUserList.value.slice(start, end)
})

watch([keyword, roleFilter, statusFilter], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

async function loadUserList() {
  try {
    loading.value = true
    const response = await getUserList()

    if (response.code !== 20000) {
      alert(response.message || '加载用户列表失败')
      return
    }

    userList.value = response.data
  } catch (error) {
    alert('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function getRoleText(role: UserRole) {
  if (role === 'ROLE1') return '普通用户'
  if (role === 'ROLE2') return '业务管理员'
  return '系统管理员'
}

function getStatusText(status: UserStatus) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function getStatusType(status: UserStatus) {
  return status === 'ENABLED' ? 'success' : 'danger'
}

async function handleToggleStatus(row: UserRow) {
  const nextStatus: UserStatus =
    row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'

  try {
    const response = await updateUserStatus(row.id, nextStatus)

    if (response.code !== 20000) {
      alert(response.message || '更新用户状态失败')
      return
    }

    userStore.toggleUserStatus(row.id)
    await loadUserList()

    alert(`${row.username} 已${nextStatus === 'ENABLED' ? '启用' : '停用'}`)
  } catch (error) {
    alert('更新用户状态失败')
  }
}

async function handleDelete(row: UserRow) {
  const ok = window.confirm(`确定删除用户 ${row.username} 吗？`)
  if (!ok) return

  try {
    const response = await deleteUserApi(row.id)

    if (response.code !== 20000) {
      alert(response.message || '删除用户失败')
      return
    }

    userStore.deleteUser(row.id)
    await loadUserList()

    alert(`已删除用户：${row.username}`)
  } catch (error) {
    alert('删除用户失败')
  }
}

function handleResetFilter() {
  keyword.value = ''
  roleFilter.value = 'ALL'
  statusFilter.value = 'ALL'
}

function handlePageChange(page: number) {
  currentPage.value = page
}

function handleSizeChange(size: number) {
  pageSize.value = size
}

onMounted(() => {
  void loadUserList()
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
          placeholder="请输入用户名或姓名"
          clearable
          style="max-width: 280px;"
        />

        <el-select
          v-model="roleFilter"
          placeholder="请选择角色"
          style="width: 180px;"
        >
          <el-option label="全部角色" value="ALL" />
          <el-option label="普通用户" value="ROLE1" />
          <el-option label="业务管理员" value="ROLE2" />
          <el-option label="系统管理员" value="ROLE3" />
        </el-select>

        <el-select
          v-model="statusFilter"
          placeholder="请选择状态"
          style="width: 180px;"
        >
          <el-option label="全部状态" value="ALL" />
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
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
      <el-table :data="pagedUserList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column prop="realName" label="姓名" width="160" />
        <el-table-column prop="role" label="角色" width="140">
          <template #default="scope">
            {{ getRoleText(scope.row.role) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button size="small" type="warning" @click="handleToggleStatus(scope.row)">
              {{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>

            <el-button size="small" type="danger" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="pagedUserList.length === 0"
        style="padding: 24px 0 8px; text-align: center; color: #999;"
      >
        没有找到符合条件的用户
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