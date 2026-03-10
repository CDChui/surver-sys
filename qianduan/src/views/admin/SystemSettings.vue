<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type SettingType = 'basic' | 'integration'

const route = useRoute()
const router = useRouter()

const activeKey = computed<SettingType>(() =>
  route.path.includes('/admin/settings/integration') ? 'integration' : 'basic'
)

function goTo(key: SettingType) {
  if (key === activeKey.value) return
  router.push(key === 'integration' ? '/admin/settings/integration' : '/admin/settings/basic')
}
</script>

<template>
  <div class="settings-layout">
    <el-card class="settings-nav-card">
      <div class="settings-nav-title">设置类型</div>

      <div
        :class="['settings-nav-item', { 'is-active': activeKey === 'basic' }]"
        @click="goTo('basic')"
      >
        <span>基础设置</span>
      </div>

      <div
        :class="['settings-nav-item', { 'is-active': activeKey === 'integration' }]"
        @click="goTo('integration')"
      >
        <span>第三方对接</span>
      </div>
    </el-card>

    <div class="settings-content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.settings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.settings-nav-card {
  width: 200px;
  flex-shrink: 0;
}

.settings-nav-title {
  font-weight: 700;
  margin-bottom: 12px;
}

.settings-nav-item {
  padding: 10px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  cursor: pointer;
  background: #fff;
  margin-bottom: 8px;
  transition: all 0.2s ease;
}

.settings-nav-item.is-active {
  border-color: #1677ff;
  background: #f0f7ff;
  color: #1677ff;
  font-weight: 600;
}

.settings-content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 992px) {
  .settings-layout {
    flex-direction: column;
  }

  .settings-nav-card {
    width: 100%;
  }
}
</style>
