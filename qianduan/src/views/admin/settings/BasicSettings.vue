<script setup lang="ts">
import { reactive } from 'vue'
import { getSystemSettings, saveSystemSettings } from '../../../api/settings'
import { useSettingsStore } from '../../../stores/settings'
import { appendOperationLog } from '../../../utils/log'

const settingsStore = useSettingsStore()

const form = reactive({
  systemName: settingsStore.settings.systemName,
  systemDomain: settingsStore.settings.systemDomain,
  defaultPageSize: settingsStore.settings.defaultPageSize,
  enableLog: settingsStore.settings.enableLog,
  enableResumeDraft: settingsStore.settings.enableResumeDraft,
  allowDuplicateSubmit: settingsStore.settings.allowDuplicateSubmit
})

async function handleSave() {
  const payload = {
    ...settingsStore.settings,
    systemName: form.systemName.trim(),
    systemDomain: form.systemDomain.trim(),
    defaultPageSize: form.defaultPageSize,
    enableLog: form.enableLog,
    enableResumeDraft: form.enableResumeDraft,
    allowDuplicateSubmit: form.allowDuplicateSubmit
  }

  try {
    const response = await saveSystemSettings(payload)

    if (response.code !== 20000) {
      alert(response.message || '系统设置保存失败')
      return
    }

    appendOperationLog({
      module: 'SYSTEM',
      action: 'UPDATE',
      target: `系统基础设置（${payload.systemName}）`
    })

    alert('基础设置已保存')
  } catch (error) {
    alert('系统设置保存失败')
  }
}

async function handleReset() {
  try {
    const response = await getSystemSettings()

    if (response.code !== 20000) {
      alert(response.message || '读取系统设置失败')
      return
    }

    form.systemName = response.data.systemName
    form.systemDomain = response.data.systemDomain
    form.defaultPageSize = response.data.defaultPageSize
    form.enableLog = response.data.enableLog
    form.enableResumeDraft = response.data.enableResumeDraft
    form.allowDuplicateSubmit = response.data.allowDuplicateSubmit

    alert('已重置为当前保存配置')
  } catch (error) {
    alert('读取系统设置失败')
  }
}
</script>

<template>
  <div style="max-width: 760px;">
    <div style="font-size: 16px; font-weight: 700; margin-bottom: 16px;">
      基础设置
    </div>

    <div style="margin-bottom: 20px;">
      <div style="margin-bottom: 8px; font-weight: 600;">系统名称</div>
      <el-input v-model="form.systemName" placeholder="请输入系统名称" />
    </div>

    <div style="margin-bottom: 20px;">
      <div style="margin-bottom: 8px; font-weight: 600;">系统域名</div>
      <el-input
        v-model="form.systemDomain"
        placeholder="例如：https://survey.example.com"
      />
    </div>

    <div style="margin-bottom: 20px;">
      <div style="margin-bottom: 8px; font-weight: 600;">默认分页条数</div>
      <el-select v-model="form.defaultPageSize" style="width: 220px;">
        <el-option label="5 条/页" :value="5" />
        <el-option label="10 条/页" :value="10" />
        <el-option label="20 条/页" :value="20" />
      </el-select>
    </div>

    <div
      style="
        display: grid;
        grid-template-columns: 1fr;
        gap: 16px;
        margin-bottom: 24px;
      "
    >
      <el-card>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
          "
        >
          <div>
            <div style="font-weight: 600; margin-bottom: 4px;">开启日志记录</div>
            <div style="color: #666;">控制后台操作日志是否记录</div>
          </div>
          <el-switch v-model="form.enableLog" />
        </div>
      </el-card>

      <el-card>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
          "
        >
          <div>
            <div style="font-weight: 600; margin-bottom: 4px;">开启移动端断点续答</div>
            <div style="color: #666;">控制答题过程中是否自动保存草稿</div>
          </div>
          <el-switch v-model="form.enableResumeDraft" />
        </div>
      </el-card>

      <el-card>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
          "
        >
          <div>
            <div style="font-weight: 600; margin-bottom: 4px;">允许重复提交</div>
            <div style="color: #666;">控制同一用户是否可重复提交同一问卷</div>
          </div>
          <el-switch v-model="form.allowDuplicateSubmit" />
        </div>
      </el-card>
    </div>

    <div style="display: flex; gap: 12px;">
      <el-button type="primary" @click="handleSave">
        保存基础设置
      </el-button>

      <el-button @click="handleReset">
        重置
      </el-button>
    </div>
  </div>
</template>
