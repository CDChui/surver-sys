<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  createUserApi,
  deleteUserApi,
  getUserList,
  resetUserPasswordApi,
  updateUserApi,
  updateUserStatus,
  type CreateUserParams,
  type UpdateUserParams,
  type UserItemResult,
  type UserRole,
  type UserStatus
} from '../../api/user'
import { useUserStore } from '../../stores/user'
import { appendOperationLog } from '../../utils/log'

type RoleFilter = 'ALL' | UserRole
type StatusFilter = 'ALL' | UserStatus
type AccountTypeFilter = 'LOCAL' | 'THIRD_PARTY'
type UserRow = UserItemResult

const userStore = useUserStore()

const keyword = ref('')
const roleFilter = ref<RoleFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')
const accountTypeFilter = ref<AccountTypeFilter>('LOCAL')

const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const creating = ref(false)
const editing = ref(false)
const resettingPassword = ref(false)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const resetDialogVisible = ref(false)
const editingUserId = ref<number | null>(null)
const editUsername = ref('')
const editIsLocalAccount = ref(true)
const resetTargetUser = ref<UserRow | null>(null)

const userList = ref<UserRow[]>([])
const createForm = reactive<CreateUserParams>({
  username: '',
  realName: '',
  remark: '',
  role: 'ROLE1',
  status: 'ENABLED',
  initialPassword: ''
})
const editForm = reactive<Omit<UpdateUserParams, 'id'>>({
  realName: '',
  remark: '',
  role: 'ROLE1',
  status: 'ENABLED'
})
const resetPasswordForm = reactive({
  newPassword: '',
  confirmPassword: ''
})

const localUserCount = computed(
  () => userList.value.filter((item) => item.localAccount).length
)

const thirdPartyUserCount = computed(
  () => userList.value.filter((item) => !item.localAccount).length
)

const filteredUserList = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return userList.value.filter((item) => {
    const matchType =
      accountTypeFilter.value === 'LOCAL' ? item.localAccount : !item.localAccount

    const matchKeyword =
      !text ||
      item.username.toLowerCase().includes(text) ||
      item.realName.toLowerCase().includes(text) ||
      item.remark.toLowerCase().includes(text)

    const matchRole =
      roleFilter.value === 'ALL' || item.role === roleFilter.value

    const matchStatus =
      statusFilter.value === 'ALL' || item.status === statusFilter.value

    return matchType && matchKeyword && matchRole && matchStatus
  })
})

const total = computed(() => filteredUserList.value.length)

const pagedUserList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredUserList.value.slice(start, end)
})

watch([keyword, roleFilter, statusFilter, accountTypeFilter], () => {
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

function getAccountTypeText(localAccount: boolean) {
  return localAccount ? '本地用户' : '第三方用户'
}

function switchAccountType(next: AccountTypeFilter) {
  accountTypeFilter.value = next
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

    appendOperationLog({
      module: 'USER',
      action: 'UPDATE',
      target: `${row.username}（${nextStatus === 'ENABLED' ? '启用' : '停用'}）`
    })

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

    appendOperationLog({
      module: 'USER',
      action: 'DELETE',
      target: row.username
    })

    alert(`已删除用户：${row.username}`)
  } catch (error) {
    alert('删除用户失败')
  }
}

function handleOpenEdit(row: UserRow) {
  editingUserId.value = row.id
  editUsername.value = row.username
  editIsLocalAccount.value = row.localAccount
  editForm.realName = row.realName
  editForm.remark = row.remark || ''
  editForm.role = row.role
  editForm.status = row.status
  editDialogVisible.value = true
}

async function handleSaveEdit() {
  const id = editingUserId.value
  if (!id) return

  const realName = editForm.realName.trim()
  if (!realName) {
    alert('请输入名称')
    return
  }
  const remark = editForm.remark.trim()
  if (remark.length > 255) {
    alert('备注长度不能超过 255 个字符')
    return
  }

  const persistedRealName = editIsLocalAccount.value
    ? realName
    : userList.value.find((item) => item.id === id)?.realName || realName

  try {
    editing.value = true
    const response = await updateUserApi({
      id,
      realName: persistedRealName,
      remark,
      role: editForm.role,
      status: editForm.status
    })

    if (response.code !== 20000) {
      alert(response.message || '保存修改失败')
      return
    }

    await loadUserList()
    editDialogVisible.value = false

    appendOperationLog({
      module: 'USER',
      action: 'UPDATE',
      target: editUsername.value
    })

    alert(`已更新用户：${editUsername.value}`)
  } catch (error) {
    const message =
      error instanceof Error && error.message ? error.message : '保存修改失败'
    alert(message)
  } finally {
    editing.value = false
  }
}

function resetCreateForm() {
  createForm.username = ''
  createForm.realName = ''
  createForm.remark = ''
  createForm.role = 'ROLE1'
  createForm.status = 'ENABLED'
  createForm.initialPassword = ''
}

function handleOpenCreate() {
  if (accountTypeFilter.value !== 'LOCAL') {
    alert('第三方用户由第三方认证平台登录后自动创建')
    return
  }

  resetCreateForm()
  createDialogVisible.value = true
}

async function handleCreateUser() {
  const username = createForm.username.trim()
  const realName = createForm.realName.trim()
  const remark = createForm.remark.trim()
  const initialPassword = createForm.initialPassword.trim()

  if (!username) {
    alert('请输入账号')
    return
  }

  if (!realName) {
    alert('请输入名称')
    return
  }

  if (remark.length > 255) {
    alert('备注长度不能超过 255 个字符')
    return
  }

  if (!initialPassword) {
    alert('请输入初始密码')
    return
  }

  if (initialPassword.length < 6 || initialPassword.length > 64) {
    alert('初始密码长度需为 6-64 位')
    return
  }

  try {
    creating.value = true

    const response = await createUserApi({
      username,
      realName,
      remark,
      role: createForm.role,
      status: createForm.status,
      initialPassword
    })

    if (response.code !== 20000) {
      alert(response.message || '新建用户失败')
      return
    }

    await loadUserList()
    createDialogVisible.value = false
    resetCreateForm()

    appendOperationLog({
      module: 'USER',
      action: 'CREATE',
      target: username
    })

    alert(`已创建本地用户：${username}`)
  } catch (error) {
    const message =
      error instanceof Error && error.message ? error.message : '新建用户失败'
    alert(message)
  } finally {
    creating.value = false
  }
}

function openResetPasswordDialog(row: UserRow) {
  if (!row.localAccount) {
    alert('仅本地用户支持重置密码')
    return
  }

  resetTargetUser.value = row
  resetPasswordForm.newPassword = ''
  resetPasswordForm.confirmPassword = ''
  resetDialogVisible.value = true
}

async function handleConfirmResetPassword() {
  const target = resetTargetUser.value
  if (!target) return

  const nextPassword = resetPasswordForm.newPassword.trim()
  const confirmPassword = resetPasswordForm.confirmPassword.trim()

  if (!nextPassword) {
    alert('请输入新密码')
    return
  }

  if (nextPassword.length < 6 || nextPassword.length > 64) {
    alert('新密码长度需为 6-64 位')
    return
  }

  if (nextPassword !== confirmPassword) {
    alert('两次输入的新密码不一致')
    return
  }

  try {
    resettingPassword.value = true
    const response = await resetUserPasswordApi(target.id, nextPassword)

    if (response.code !== 20000) {
      alert(response.message || '重置密码失败')
      return
    }

    resetDialogVisible.value = false

    appendOperationLog({
      module: 'USER',
      action: 'UPDATE',
      target: `RESET_PASSWORD:${target.username}`
    })

    alert(`已重置用户 ${target.username} 的密码`)
  } catch (error) {
    const message =
      error instanceof Error && error.message ? error.message : '重置密码失败'
    alert(message)
  } finally {
    resettingPassword.value = false
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
  <div style="display: flex; gap: 16px; align-items: flex-start;">
    <el-card style="width: 200px; flex-shrink: 0;">
      <div style="font-weight: 700; margin-bottom: 12px;">用户类型</div>

      <div
        :style="{
          padding: '10px 12px',
          borderRadius: '8px',
          cursor: 'pointer',
          marginBottom: '8px',
          border: accountTypeFilter === 'LOCAL' ? '1px solid #1677ff' : '1px solid #e5e6eb',
          background: accountTypeFilter === 'LOCAL' ? '#f0f7ff' : '#fff'
        }"
        @click="switchAccountType('LOCAL')"
      >
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>本地用户</span>
          <el-tag type="info">{{ localUserCount }}</el-tag>
        </div>
      </div>

      <div
        :style="{
          padding: '10px 12px',
          borderRadius: '8px',
          cursor: 'pointer',
          border: accountTypeFilter === 'THIRD_PARTY' ? '1px solid #1677ff' : '1px solid #e5e6eb',
          background: accountTypeFilter === 'THIRD_PARTY' ? '#f0f7ff' : '#fff'
        }"
        @click="switchAccountType('THIRD_PARTY')"
      >
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>第三方用户</span>
          <el-tag type="info">{{ thirdPartyUserCount }}</el-tag>
        </div>
      </div>
    </el-card>

    <div style="flex: 1; min-width: 0;">
      <el-card style="margin-bottom: 16px;">
        <div
          style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"
        >
          <el-input
            v-model="keyword"
            placeholder="请输入账号或名称"
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

          <el-button
            v-if="accountTypeFilter === 'LOCAL'"
            type="primary"
            @click="handleOpenCreate"
          >
            新建本地用户
          </el-button>

          <span style="color: #666;">
            当前结果：{{ total }} 条
          </span>
        </div>
      </el-card>

      <el-card v-loading="loading">
        <el-table :data="pagedUserList" style="width: 100%">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="username" label="账号" width="180" />
          <el-table-column prop="realName" label="名称" width="160" />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="用户类型" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.localAccount ? 'success' : 'info'">
                {{ getAccountTypeText(scope.row.localAccount) }}
              </el-tag>
            </template>
          </el-table-column>
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
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="420">
            <template #default="scope">
              <el-button size="small" @click="handleOpenEdit(scope.row)">
                编辑
              </el-button>

              <el-button size="small" type="warning" @click="handleToggleStatus(scope.row)">
                {{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>

              <el-button
                v-if="scope.row.localAccount"
                size="small"
                type="primary"
                plain
                @click="openResetPasswordDialog(scope.row)"
              >
                重置密码
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

    <el-dialog v-model="createDialogVisible" title="新建本地用户" width="520px">
      <div style="display: grid; gap: 14px;">
        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">账号</div>
          <el-input v-model="createForm.username" placeholder="请输入账号" />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">名称</div>
          <el-input v-model="createForm.realName" placeholder="请输入名称" />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">备注</div>
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="请输入备注（可选）"
          />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">初始密码</div>
          <el-input
            v-model="createForm.initialPassword"
            type="password"
            show-password
            placeholder="请输入初始密码（6-64位）"
          />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">角色</div>
          <el-select v-model="createForm.role" style="width: 100%;">
            <el-option label="普通用户" value="ROLE1" />
            <el-option label="业务管理员" value="ROLE2" />
            <el-option label="系统管理员" value="ROLE3" />
          </el-select>
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">状态</div>
          <el-select v-model="createForm.status" style="width: 100%;">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <template #footer>
        <el-button @click="createDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateUser">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑用户" width="520px">
      <div style="display: grid; gap: 14px;">
        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">账号</div>
          <el-input :model-value="editUsername" disabled />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">名称</div>
          <el-input
            v-model="editForm.realName"
            :disabled="!editIsLocalAccount"
            placeholder="请输入名称"
          />
          <div v-if="!editIsLocalAccount" style="margin-top: 6px; color: #999; font-size: 12px;">
            第三方用户名称由认证平台同步，不可修改
          </div>
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">备注</div>
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="请输入备注（可选）"
          />
          <div v-if="!editIsLocalAccount" style="margin-top: 6px; color: #999; font-size: 12px;">
            第三方用户账号与名称不可修改，备注可用于补充说明
          </div>
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">角色</div>
          <el-select v-model="editForm.role" style="width: 100%;">
            <el-option label="普通用户" value="ROLE1" />
            <el-option label="业务管理员" value="ROLE2" />
            <el-option label="系统管理员" value="ROLE3" />
          </el-select>
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">状态</div>
          <el-select v-model="editForm.status" style="width: 100%;">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <template #footer>
        <el-button @click="editDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="editing" @click="handleSaveEdit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resetDialogVisible"
      :title="`重置密码：${resetTargetUser?.username || ''}`"
      width="520px"
    >
      <div style="display: grid; gap: 14px;">
        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">新密码</div>
          <el-input
            v-model="resetPasswordForm.newPassword"
            type="password"
            show-password
            placeholder="请输入新密码（6-64位）"
          />
        </div>

        <div>
          <div style="margin-bottom: 6px; font-weight: 600;">确认新密码</div>
          <el-input
            v-model="resetPasswordForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="resettingPassword"
          @click="handleConfirmResetPassword"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
