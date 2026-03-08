<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePermissionStore, type RoleCode } from '../../stores/permission'
import { appendOperationLog } from '../../utils/log'

const permissionStore = usePermissionStore()

const currentRole = ref<RoleCode>('ROLE1')

const permissionGroups = [
  {
    groupName: '问卷管理',
    permissions: [
      'survey:list',
      'survey:create',
      'survey:edit',
      'survey:stats',
      'survey:publish',
      'survey:delete',
      'survey:auth'
    ]
  },
  {
    groupName: '用户管理',
    permissions: [
      'user:list',
      'user:edit',
      'user:delete'
    ]
  },
  {
    groupName: '授权管理',
    permissions: [
      'permission:manage'
    ]
  },
  {
    groupName: '移动端',
    permissions: [
      'survey:fill'
    ]
  }
]

const permissionTextMap: Record<string, string> = {
  'survey:list': '查看问卷列表',
  'survey:create': '创建问卷',
  'survey:edit': '编辑问卷',
  'survey:stats': '查看统计',
  'survey:publish': '发布问卷',
  'survey:delete': '删除问卷',
  'survey:auth': '问卷授权',
  'user:list': '查看用户列表',
  'user:edit': '编辑用户',
  'user:delete': '删除用户',
  'permission:manage': '管理权限',
  'survey:fill': '填写问卷'
}

function getRolePermissions(role: RoleCode) {
  return [...(permissionStore.rolePermissionMap[role] || [])]
}

const checkedPermissions = ref<string[]>(getRolePermissions(currentRole.value))
const isSystemAdminRole = computed(() => currentRole.value === 'ROLE3')

watch(currentRole, (role) => {
  checkedPermissions.value = getRolePermissions(role)
})

const permissionCount = computed(() => checkedPermissions.value.length)

function getRoleText(role: RoleCode) {
  if (role === 'ROLE1') return '普通用户'
  if (role === 'ROLE2') return '业务管理员'
  return '系统管理员'
}

function getPermissionText(permission: string) {
  return permissionTextMap[permission] || permission
}

function handleSave() {
  if (isSystemAdminRole.value) {
    checkedPermissions.value = getRolePermissions('ROLE3')
    alert('系统管理员默认拥有全部权限，且不可修改')
    return
  }

  permissionStore.setRolePermissions(currentRole.value, [...checkedPermissions.value])
  checkedPermissions.value = getRolePermissions(currentRole.value)
  appendOperationLog({
    module: 'PERMISSION',
    action: 'UPDATE',
    target: `${getRoleText(currentRole.value)} 权限配置`
  })
  alert(`已保存 ${getRoleText(currentRole.value)} 的权限配置`)
}

function handleReset() {
  if (isSystemAdminRole.value) {
    checkedPermissions.value = getRolePermissions('ROLE3')
    alert('系统管理员默认拥有全部权限，且不可修改')
    return
  }

  checkedPermissions.value = []
  checkedPermissions.value = getRolePermissions(currentRole.value)
  alert(`已重置 ${getRoleText(currentRole.value)} 的权限配置`)
}
</script>

<template>
  <div>
    <el-card style="margin-bottom: 16px;">
      <div
        style="display: flex; gap: 16px; align-items: center; flex-wrap: wrap;"
      >
        <div>
          <span style="margin-right: 8px; color: #666;">当前角色：</span>
          <el-select v-model="currentRole" style="width: 180px;">
            <el-option label="普通用户" value="ROLE1" />
            <el-option label="业务管理员" value="ROLE2" />
            <el-option label="系统管理员" value="ROLE3" />
          </el-select>
        </div>

        <div style="color: #666;">
          当前已勾选权限：{{ permissionCount }} 项
        </div>

        <div v-if="isSystemAdminRole" style="color: #e6a23c;">
          系统管理员默认拥有全部权限，且不可修改
        </div>

        <div style="display: flex; gap: 12px;">
          <el-button type="primary" :disabled="isSystemAdminRole" @click="handleSave">
            保存权限配置
          </el-button>

          <el-button :disabled="isSystemAdminRole" @click="handleReset">
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card
      v-for="group in permissionGroups"
      :key="group.groupName"
      style="margin-bottom: 16px;"
    >
      <template #header>
        <div style="font-weight: 700;">
          {{ group.groupName }}
        </div>
      </template>

      <el-checkbox-group v-model="checkedPermissions">
        <div
          style="
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 12px;
          "
        >
          <el-checkbox
            v-for="permission in group.permissions"
            :key="permission"
            :value="permission"
            :disabled="isSystemAdminRole"
          >
            {{ getPermissionText(permission) }}
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </el-card>
  </div>
</template>
