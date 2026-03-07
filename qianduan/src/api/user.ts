import request from './request'
import { USE_REAL_API } from '../config/env'
import { useUserStore } from '../stores/user'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface UserItemResult {
  id: number
  username: string
  realName: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

export async function getUserList(): Promise<ApiResponse<UserItemResult[]>> {
  if (USE_REAL_API) {
    return request.get('/users')
  }

  const userStore = useUserStore()

  return {
    code: 20000,
    message: 'success',
    data: userStore.userList
  }
}

export async function updateUserRole(
  id: number,
  role: UserRole
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/users/${id}/role`, { role })
  }

  void id
  void role

  return {
    code: 20000,
    message: '角色更新成功',
    data: null
  }
}

export async function updateUserStatus(
  id: number,
  status: UserStatus
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/users/${id}/status`, { status })
  }

  void id
  void status

  return {
    code: 20000,
    message: '状态更新成功',
    data: null
  }
}

export async function deleteUserApi(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.delete(`/users/${id}`)
  }

  void id

  return {
    code: 20000,
    message: '删除成功',
    data: null
  }
}