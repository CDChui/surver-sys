<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logoutApi } from '../../api/auth'
import { getPublicSystemBranding } from '../../api/settings'
import { getMySurveySubmissions, type MySurveySubmissionItemResult } from '../../api/survey'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { setLogoutContext } from '../../utils/logout-context'

const router = useRouter()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

const loading = ref(false)
const records = ref<MySurveySubmissionItemResult[]>([])
const compatHint = ref('')

const pageTitle = computed(
  () => `${(settingsStore.settings.systemName || '系统').trim() || '系统'}个人首页`
)
const userHomeLogo = computed(() => settingsStore.settings.userHomeLogo || '')

async function handleLogout() {
  const currentRole = authStore.role
  setLogoutContext('user', currentRole)

  try {
    await logoutApi()
  } catch (error) {
    // keep local logout flow even when server logout fails
  }

  authStore.clearToken()
  await router.replace({
    path: '/auth/logout',
    query: {
      role: currentRole,
      entry: 'user'
    }
  })
}

async function loadBranding() {
  try {
    const response = await getPublicSystemBranding()

    if (response.code !== 20000 || !response.data) {
      return
    }

    settingsStore.saveSettings({
      ...settingsStore.settings,
      systemName: response.data.systemName || settingsStore.settings.systemName,
      adminLogo: response.data.adminLogo || '',
      userHomeLogo: response.data.userHomeLogo || '',
      titleLogo: response.data.titleLogo || ''
    })
  } catch (error) {
    // keep homepage resilient when branding endpoint is temporarily unavailable
  }
}

async function loadRecords() {
  try {
    loading.value = true
    const response = await getMySurveySubmissions()

    if (response.code !== 20000) {
      alert(response.message || '加载已答问卷失败')
      return
    }

    compatHint.value =
      response.message === 'LOCAL_FALLBACK_MISSING_ENDPOINT'
        ? '检测到后端暂未提供“个人答卷记录接口”，当前仅显示本地缓存记录。请重启后端后刷新。'
        : ''

    records.value = response.data
  } catch (error) {
    alert('加载已答问卷失败')
  } finally {
    loading.value = false
  }
}

function viewSubmission(item: MySurveySubmissionItemResult) {
  router.push({
    path: '/m/review',
    query: {
      id: String(item.surveyId)
    }
  })
}

onMounted(() => {
  void loadBranding()
  void loadRecords()
})
</script>

<template>
  <div class="page-bg">
    <div class="page-shell">
      <div class="card hero-card">
        <div class="home-logo-wrap">
          <img
            v-if="userHomeLogo"
            :src="userHomeLogo"
            alt="用户主页 Logo"
            class="home-logo"
          >
          <div v-else class="home-logo-placeholder">LOGO</div>
        </div>

        <h1 class="page-title">{{ pageTitle }}</h1>
      </div>

      <div class="card">
        <div class="info-header">
          <div class="section-title">个人信息</div>
          <van-button
            size="small"
            plain
            type="primary"
            class="logout-btn"
            @click="handleLogout"
          >
            退出登录
          </van-button>
        </div>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">账号：</span>
            <span class="info-value">{{ authStore.username || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">名称：</span>
            <span class="info-value">{{ authStore.realName || '-' }}</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div
          v-if="compatHint"
          class="compat-hint"
        >
          {{ compatHint }}
        </div>

        <div class="record-header">
          <div class="section-title">问卷记录</div>
          <van-button class="refresh-btn" size="small" plain @click="loadRecords">
            刷新
          </van-button>
        </div>

        <div v-if="loading" class="loading-wrap">
          <van-loading size="20px">加载中...</van-loading>
        </div>

        <div v-else-if="records.length === 0" class="empty-text">
          暂无已答问卷
        </div>

        <div v-else class="record-list">
          <van-cell
            v-for="item in records"
            :key="item.surveyId"
            class="record-cell"
            :title="item.surveyTitle"
            :label="item.submitTime ? `提交时间：${item.submitTime}` : '已提交'"
            is-link
            @click="viewSubmission(item)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-bg {
  min-height: 100vh;
  padding: 16px;
  background: linear-gradient(180deg, #f3f5f8 0%, #eef2f7 100%);
}

.page-shell {
  max-width: 760px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.card {
  background: #fff;
  border: 1px solid #e9edf3;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 12px 20px -18px rgba(18, 39, 75, 0.45);
}

.hero-card {
  background: linear-gradient(145deg, #ffffff 0%, #f8fbff 100%);
  border-left: 4px solid #2f8bff;
}

.home-logo-wrap {
  width: 100%;
  min-height: 70px;
  border-radius: 10px;
  border: 1px dashed #d5deea;
  background: #f9fbfe;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.home-logo {
  max-height: 48px;
  max-width: 100%;
  object-fit: contain;
}

.home-logo-placeholder {
  color: #9aa4b2;
  letter-spacing: 0.6px;
  font-size: 12px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
  letter-spacing: 0.2px;
  text-align: center;
}

.info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.logout-btn {
  border-radius: 999px;
  flex-shrink: 0;
}

.section-title {
  font-weight: 700;
  margin-bottom: 10px;
}

.info-header .section-title {
  margin-bottom: 0;
}

.info-grid {
  display: grid;
  gap: 10px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  line-height: 1.9;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  background: #fafcff;
  padding: 8px 12px;
}

.info-label {
  font-weight: 600;
}

.compat-hint {
  margin-bottom: 12px;
  border: 1px solid #ffe58f;
  background: #fffbe6;
  color: #ad6800;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
}

.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.record-header .section-title {
  margin-bottom: 0;
}

.refresh-btn {
  border-radius: 8px;
}

.loading-wrap {
  text-align: center;
  padding: 24px 0;
}

.empty-text {
  color: #9aa3ad;
  text-align: center;
  padding: 24px 0;
}

.record-list {
  display: grid;
  gap: 8px;
}

:deep(.record-cell.van-cell) {
  border: 1px solid #edf1f7;
  border-radius: 10px;
  background: #fbfcff;
  transition: all 0.2s ease;
}

:deep(.record-cell.van-cell:active) {
  background: #f1f7ff;
  border-color: #d8e9ff;
}

:deep(.record-cell .van-cell__title) {
  font-weight: 600;
  color: #1f2a3a;
}

:deep(.record-cell .van-cell__label) {
  margin-top: 6px;
  color: #7f8b99;
}

@media (max-width: 640px) {
  .page-bg {
    padding: 12px;
  }

  .card {
    padding: 16px;
  }

  .page-title {
    font-size: 23px;
  }

  .info-header {
    flex-wrap: wrap;
  }
}
</style>
