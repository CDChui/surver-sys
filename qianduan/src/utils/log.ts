import { createLog, type LogAction, type LogModule } from '../api/log'
import { useAuthStore } from '../stores/auth'
import { useSettingsStore } from '../stores/settings'

interface AppendOperationLogParams {
  module: LogModule
  action: LogAction
  target: string
  operator?: string
  force?: boolean
}

export function appendOperationLog(params: AppendOperationLogParams) {
  try {
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
