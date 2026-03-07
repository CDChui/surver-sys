<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePermissionStore, type RoleCode } from '../../stores/permission'

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
      'survey:delete'
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

function getRolePermissions(role: RoleCode) {
  return [...(permissionStore.rolePermissionMap[role] || [])]
}

const checkedPermissions = ref<string[]>(getRolePermissions(currentRole.value))

watch(currentRole, (role) => {
  checkedPermissions.value = getRolePermissions(role)
})

const permissionCount = computed(() => checkedPermissions.value.length)

function getRoleText(role: RoleCode) {
  if (role === 'ROLE1') return '普通用户'
  if (role === 'ROLE2') return '业务管理员'
  return '系统管理员'
}

function handleSave() {
  permissionStore.setRolePermissions(currentRole.value, [...checkedPermissions.value])
  checkedPermissions.value = getRolePermissions(currentRole.value)
  alert(`已保存 ${getRoleText(currentRole.value)} 的权限配置`)
}

function handleReset() {
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

        <div style="display: flex; gap: 12px;">
          <el-button type="primary" @click="handleSave">
            保存权限配置
          </el-button>

          <el-button @click="handleReset">
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
          >
            {{ permission }}
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </el-card>
  </div>
</template>