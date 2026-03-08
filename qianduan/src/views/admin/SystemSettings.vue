<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeTab = computed(() =>
  route.path.includes('/admin/settings/integration') ? 'integration' : 'basic'
)

function handleTabChange(tabName: string) {
  if (tabName === 'integration') {
    router.push('/admin/settings/integration')
    return
  }

  router.push('/admin/settings/basic')
}
</script>

<template>
  <div>
    <el-card>
      <el-tabs :model-value="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="基础设置" name="basic" />
        <el-tab-pane label="第三方对接" name="integration" />
      </el-tabs>

      <router-view />
    </el-card>
  </div>
</template>
