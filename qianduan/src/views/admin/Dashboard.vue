<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSurveyStore } from '../../stores/survey'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const surveyStore = useSurveyStore()
const userStore = useUserStore()

const surveyTotal = computed(() => surveyStore.surveyList.length)

const publishedSurveyTotal = computed(() => {
  return surveyStore.surveyList.filter((item) => item.status === 'PUBLISHED').length
})

const userTotal = computed(() => userStore.userList.length)

const enabledUserTotal = computed(() => {
  return userStore.userList.filter((item) => item.status === 'ENABLED').length
})

const recentSurveys = computed(() => {
  return [...surveyStore.surveyList].slice(0, 5)
})

function getStatusText(status: 'DRAFT' | 'PUBLISHED' | 'CLOSED') {
  if (status === 'DRAFT') return '草稿'
  if (status === 'PUBLISHED') return '已发布'
  return '已关闭'
}

function getStatusType(status: 'DRAFT' | 'PUBLISHED' | 'CLOSED') {
  if (status === 'DRAFT') return 'info'
  if (status === 'PUBLISHED') return 'success'
  return 'danger'
}

function goSurveys() {
  router.push('/admin/surveys')
}

function goUsers() {
  router.push('/admin/users')
}

function goPermissions() {
  router.push('/admin/permissions')
}
</script>

<template>
  <div>
    <div
      style="
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      "
    >
      <el-card>
        <div style="color: #666; margin-bottom: 8px;">问卷总数</div>
        <div style="font-size: 30px; font-weight: 700;">{{ surveyTotal }}</div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">已发布问卷</div>
        <div style="font-size: 30px; font-weight: 700; color: #67c23a;">
          {{ publishedSurveyTotal }}
        </div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">用户总数</div>
        <div style="font-size: 30px; font-weight: 700;">{{ userTotal }}</div>
      </el-card>

      <el-card>
        <div style="color: #666; margin-bottom: 8px;">启用用户数</div>
        <div style="font-size: 30px; font-weight: 700; color: #409eff;">
          {{ enabledUserTotal }}
        </div>
      </el-card>
    </div>

    <div
      style="
        display: grid;
        grid-template-columns: 2fr 1fr;
        gap: 16px;
        align-items: start;
      "
    >
      <el-card>
        <template #header>
          <div style="font-weight: 700;">最近问卷</div>
        </template>

        <el-table :data="recentSurveys" style="width: 100%">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="title" label="问卷标题" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
      </el-card>

      <div style="display: flex; flex-direction: column; gap: 16px;">
        <el-card>
          <template #header>
            <div style="font-weight: 700;">快捷入口</div>
          </template>

          <div style="display: flex; flex-direction: column; gap: 12px;">
            <el-button type="primary" @click="goSurveys">
              进入问卷管理
            </el-button>
            <el-button @click="goUsers">
              进入用户管理
            </el-button>
            <el-button @click="goPermissions">
              进入授权管理
            </el-button>
          </div>
        </el-card>

        <el-card>
          <template #header>
            <div style="font-weight: 700;">系统概览</div>
          </template>

          <div style="color: #666; line-height: 1.9;">
            <div>当前系统已具备问卷管理、用户管理、授权管理、统计分析、移动端答题等核心能力。</div>
            <div style="margin-top: 10px;">
              后续建议继续对接真实后端接口，并补充数据看板图表、审核流、日志记录等能力。
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>