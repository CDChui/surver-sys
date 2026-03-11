<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type LogType = 'system' | 'user'

const route = useRoute()
const router = useRouter()

const activeKey = computed<LogType>(() =>
  route.path.includes('/admin/logs/user') ? 'user' : 'system'
)

function goTo(key: LogType) {
  if (key === activeKey.value) return
  router.push(key === 'user' ? '/admin/logs/user' : '/admin/logs/system')
}
</script>

<template>
  <div class="logs-layout">
    <el-card class="logs-nav-card">
      <div class="logs-nav-title">日志类型</div>

      <div
        :class="['logs-nav-item', { 'is-active': activeKey === 'system' }]"
        @click="goTo('system')"
      >
        <span>系统日志</span>
      </div>

      <div
        :class="['logs-nav-item', { 'is-active': activeKey === 'user' }]"
        @click="goTo('user')"
      >
        <span>用户日志</span>
      </div>
    </el-card>

    <div class="logs-content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.logs-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.logs-nav-card {
  width: 200px;
  flex-shrink: 0;
}

.logs-nav-title {
  font-weight: 700;
  margin-bottom: 12px;
}

.logs-nav-item {
  padding: 10px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  cursor: pointer;
  background: #fff;
  margin-bottom: 8px;
  transition: all 0.2s ease;
}

.logs-nav-item.is-active {
  border-color: #1677ff;
  background: #f0f7ff;
  color: #1677ff;
  font-weight: 600;
}

.logs-content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 992px) {
  .logs-layout {
    flex-direction: column;
  }

  .logs-nav-card {
    width: 100%;
  }
}
</style>
