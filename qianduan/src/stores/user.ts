import { defineStore } from 'pinia'

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface UserItem {
  id: number
  username: string
  realName: string
  remark: string
  role: UserRole
  status: UserStatus
  createdAt: string
  localAccount: boolean
}

const USER_LIST_KEY = 'USER_LIST'
const DEFAULT_LOCAL_TEST_USERNAMES = new Set([
  'admin',
  'teacher01',
  'student01',
  'counselor01',
  'student02'
])

function getDefaultUsers(): UserItem[] {
  return [
    {
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      remark: '默认管理员账号',
      role: 'ROLE3',
      status: 'ENABLED',
      createdAt: '2026-03-01 09:00',
      localAccount: true
    },
    {
      id: 2,
      username: 'teacher01',
      realName: '张老师',
      remark: '默认测试账号',
      role: 'ROLE2',
      status: 'ENABLED',
      createdAt: '2026-03-02 10:30',
      localAccount: true
    },
    {
      id: 3,
      username: 'counselor01',
      realName: '李辅导员',
      remark: '默认测试账号',
      role: 'ROLE2',
      status: 'DISABLED',
      createdAt: '2026-03-03 11:20',
      localAccount: true
    },
    {
      id: 4,
      username: 'student01',
      realName: '王同学',
      remark: '默认测试账号',
      role: 'ROLE1',
      status: 'ENABLED',
      createdAt: '2026-03-04 14:10',
      localAccount: true
    },
    {
      id: 5,
      username: 'student02',
      realName: '赵同学',
      remark: '默认测试账号',
      role: 'ROLE1',
      status: 'ENABLED',
      createdAt: '2026-03-05 15:00',
      localAccount: true
    }
  ]
}

function ensureStorage() {
  if (!localStorage.getItem(USER_LIST_KEY)) {
    localStorage.setItem(USER_LIST_KEY, JSON.stringify(getDefaultUsers()))
  }
}

function readUsers(): UserItem[] {
  ensureStorage()
  const list = JSON.parse(localStorage.getItem(USER_LIST_KEY) || '[]') as Array<
    Partial<UserItem>
  >
  return list.map((item) => ({
    id: Number(item.id) || 0,
    username: String(item.username || ''),
    realName: String(item.realName || ''),
    remark: String(item.remark || ''),
    role: (item.role as UserRole) || 'ROLE1',
    status: (item.status as UserStatus) || 'ENABLED',
    createdAt: String(item.createdAt || ''),
    localAccount: DEFAULT_LOCAL_TEST_USERNAMES.has(String(item.username || ''))
      ? true
      : Boolean(item.localAccount)
  }))
}

function writeUsers(users: UserItem[]) {
  localStorage.setItem(USER_LIST_KEY, JSON.stringify(users))
}

function getNowText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

export const useUserStore = defineStore('user', {
  state: () => ({
    userList: readUsers() as UserItem[]
  }),

  getters: {
    businessAdminList(state): UserItem[] {
      return state.userList.filter(
        (item) => item.role === 'ROLE2' && item.status === 'ENABLED'
      )
    }
  },

  actions: {
    refreshFromStorage() {
      this.userList = readUsers()
    },

    persist() {
      writeUsers(this.userList)
    },

    createUser(payload: {
      username: string
      realName: string
      remark: string
      role: UserRole
      status: UserStatus
    }) {
      const list = Array.isArray(this.userList) ? this.userList : []
      const nextId =
        list.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1

      this.userList = [
        {
          id: nextId,
          username: payload.username,
          realName: payload.realName,
          remark: payload.remark,
          role: payload.role,
          status: payload.status,
          createdAt: getNowText(),
          localAccount: true
        },
        ...list
      ]

      this.persist()
    },

    toggleUserStatus(id: number) {
      this.userList = this.userList.map((item) =>
        item.id === id
          ? {
              ...item,
              status: item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
            }
          : item
      )

      this.persist()
    },

    deleteUser(id: number) {
      this.userList = this.userList.filter((item) => item.id !== id)
      this.persist()
    }
  }
})
