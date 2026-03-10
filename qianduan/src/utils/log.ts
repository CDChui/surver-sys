import { createLog, type LogAction, type LogModule } from '../api/log'
import { useAuthStore } from '../stores/auth'
import { useSettingsStore } from '../stores/settings'
import { USE_REAL_API } from '../config/env'

interface AppendOperationLogParams {
  module: LogModule
  action: LogAction
  target: string
  operator?: string
  force?: boolean
}

export function appendOperationLog(params: AppendOperationLogParams) {
  try {
    if (USE_REAL_API) {
      // 真实后端已在服务端统一落库审计日志，前端不再重复写入
      return
    }

    const settingsStore = useSettingsStore()
    if (!params.force && !settingsStore.settings.enableLog) {
      return
    }

    const authStore = useAuthStore()
    const operator = params.operator || authStore.realName || authStore.username || '系统'

    void createLog({
      operator,
      module: params.module,
      action: params.action,
      target: params.target
    })
  } catch (error) {
    // 日志写入失败不影响主业务流程
  }
}
