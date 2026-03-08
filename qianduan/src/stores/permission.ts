import { defineStore } from 'pinia'

export type RoleCode = 'ROLE1' | 'ROLE2' | 'ROLE3'

export interface PermissionGroup {
  groupName: string
  permissions: string[]
}

const STORAGE_KEY = 'ROLE_PERMISSION_MAP'
const ROLE3_FIXED_PERMISSIONS = [
  'survey:list',
  'survey:create',
  'survey:edit',
  'survey:stats',
  'survey:publish',
  'survey:delete',
  'survey:auth',
  'user:list',
  'user:edit',
  'user:delete',
  'permission:manage',
  'survey:fill'
]

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
    ROLE3: [...ROLE3_FIXED_PERMISSIONS]
  }
}

function normalizeMap(map: Partial<Record<RoleCode, string[]>>) {
  const defaultMap = getDefaultMap()

  return {
    ROLE1: Array.from(new Set(map.ROLE1 || defaultMap.ROLE1)),
    ROLE2: Array.from(new Set(map.ROLE2 || defaultMap.ROLE2)),
    ROLE3: [...ROLE3_FIXED_PERMISSIONS]
  } as Record<RoleCode, string[]>
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultMap()))
  }
}

function readMap(): Record<RoleCode, string[]> {
  ensureStorage()
  const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') as Partial<
    Record<RoleCode, string[]>
  >
  return normalizeMap(parsed)
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
      if (role === 'ROLE3') {
        this.rolePermissionMap = {
          ...this.rolePermissionMap,
          ROLE3: [...ROLE3_FIXED_PERMISSIONS]
        }
        this.persist()
        return
      }

      this.rolePermissionMap = {
        ...this.rolePermissionMap,
        [role]: Array.from(new Set(permissions))
      }
      this.persist()
    }
  }
})
