import { defineStore } from 'pinia'

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface UserItem {
  id: number
  username: string
  realName: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

const USER_LIST_KEY = 'USER_LIST'

function getDefaultUsers(): UserItem[] {
  return [
    {
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      role: 'ROLE3',
      status: 'ENABLED',
      createdAt: '2026-03-01 09:00'
    },
    {
      id: 2,
      username: 'teacher01',
      realName: '张老师',
      role: 'ROLE2',
      status: 'ENABLED',
      createdAt: '2026-03-02 10:30'
    },
    {
      id: 3,
      username: 'counselor01',
      realName: '李辅导员',
      role: 'ROLE2',
      status: 'DISABLED',
      createdAt: '2026-03-03 11:20'
    },
    {
      id: 4,
      username: 'student01',
      realName: '王同学',
      role: 'ROLE1',
      status: 'ENABLED',
      createdAt: '2026-03-04 14:10'
    },
    {
      id: 5,
      username: 'student02',
      realName: '赵同学',
      role: 'ROLE1',
      status: 'ENABLED',
      createdAt: '2026-03-05 15:00'
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
  return JSON.parse(localStorage.getItem(USER_LIST_KEY) || '[]')
}

function writeUsers(users: UserItem[]) {
  localStorage.setItem(USER_LIST_KEY, JSON.stringify(users))
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