import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { USE_REAL_API } from './config/env'
import { getPublicSystemBranding, getSystemSettings } from './api/settings'
import { useAuthStore } from './stores/auth'
import { useSettingsStore } from './stores/settings'

// Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// Vant
import Vant from 'vant'
import 'vant/lib/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.use(ElementPlus)
app.use(Vant)

async function bootstrapSystemSettings() {
  if (!USE_REAL_API) return

  const authStore = useAuthStore(pinia)
  if (!authStore.token) return

  const settingsStore = useSettingsStore(pinia)

  try {
    const response = await getSystemSettings()
    if (response.code === 20000 && response.data) {
      settingsStore.saveSettings(response.data)
      return
    }
  } catch (error) {
    // for non-admin users fallback to public branding settings
  }

  try {
    const brandingResponse = await getPublicSystemBranding()
    if (brandingResponse.code === 20000 && brandingResponse.data) {
      settingsStore.saveSettings({
        ...settingsStore.settings,
        systemName: brandingResponse.data.systemName || settingsStore.settings.systemName,
        adminLogo: brandingResponse.data.adminLogo || '',
        userHomeLogo: brandingResponse.data.userHomeLogo || ''
      })
    }
  } catch (error) {
    // keep startup resilient when remote settings are temporarily unavailable
  }
}

void bootstrapSystemSettings().finally(() => {
  app.mount('#app')
})
