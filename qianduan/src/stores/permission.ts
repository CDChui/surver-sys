import { defineStore } from 'pinia'

export type RoleCode = 'ROLE1' | 'ROLE2' | 'ROLE3'

export interface PermissionGroup {
  groupName: string
  permissions: string[]
}

const STORAGE_KEY = 'ROLE_PERMISSION_MAP'

function getDefaultMap(): Record<RoleCode, string[]> {
  return {
    ROLE1: ['survey:fill'],
    ROLE2: [
      'survey:list',
      'survey:create',
      'survey:edit',
      'survey:stats',
      'survey:publish'
    ],
    ROLE3: [
      'survey:list',
      'survey:create',
      'survey:edit',
      'survey:stats',
      'survey:publish',
      'survey:delete',
      'user:list',
      'user:edit',
      'user:delete',
      'permission:manage'
    ]
  }
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultMap()))
  }
}

function readMap(): Record<RoleCode, string[]> {
  ensureStorage()
  return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
}

function writeMap(map: Record<RoleCode, string[]>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    rolePermissionMap: readMap() as Record<RoleCode, string[]>
  }),

  actions: {
    persist() {
      writeMap(this.rolePermissionMap)
    },

    setRolePermissions(role: RoleCode, permissions: string[]) {
      this.rolePermissionMap = {
        ...this.rolePermissionMap,
        [role]: [...permissions]
      }
      this.persist()
    }
  }
})