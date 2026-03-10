import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePermissionStore } from '../stores/permission'
import { useSettingsStore } from '../stores/settings'
import MobileHome from '../views/mobile/Home.vue'
import SurveyPage from '../views/mobile/Survey.vue'
import SubmitSuccess from '../views/mobile/SubmitSuccess.vue'
import BlockedQuota from '../views/mobile/BlockedQuota.vue'
import BlockedDuplicate from '../views/mobile/BlockedDuplicate.vue'
import ReviewSubmitted from '../views/mobile/ReviewSubmitted.vue'
import ErrorPage from '../views/mobile/ErrorPage.vue'
import SsoCallback from '../views/auth/SsoCallback.vue'
import LogoutPage from '../views/auth/Logout.vue'
import AdminLogin from '../views/admin/Login.vue'
import AdminLayout from '../views/admin/AdminLayout.vue'
import Dashboard from '../views/admin/Dashboard.vue'
import SurveyList from '../views/admin/SurveyList.vue'
import CreateSurvey from '../views/admin/CreateSurvey.vue'
import EditSurvey from '../views/admin/EditSurvey.vue'
import SurveyStats from '../views/admin/SurveyStats.vue'
import SurveyAuth from '../views/admin/SurveyAuth.vue'
import UserManagement from '../views/admin/UserManagement.vue'
import PermissionManagement from '../views/admin/PermissionManagement.vue'
import LogManagement from '../views/admin/LogManagement.vue'
import SystemSettings from '../views/admin/SystemSettings.vue'
import ChangePassword from '../views/admin/ChangePassword.vue'
import BasicSettings from '../views/admin/settings/BasicSettings.vue'
import ThirdPartyIntegration from '../views/admin/settings/ThirdPartyIntegration.vue'
import { buildLoginPathWithRedirect, getPostLoginPath } from '../utils/auth-redirect'
import {
  beginThirdPartyLogin,
  shouldAutoThirdPartyForAdmin
} from '../utils/oauth-login'
import { getPublicAuthSettings } from '../api/settings'

const routes = [
  {
    path: '/',
    redirect: '/m'
  },
  {
    path: '/m',
    component: MobileHome,
    meta: {
      requiresLogin: true
    }
  },
  {
    path: '/m/surveys/:id',
    component: SurveyPage,
    meta: {
      requiresLogin: true
    }
  },
  {
    path: '/m/success',
    component: SubmitSuccess,
    meta: {
      requiresLogin: true
    }
  },
  {
    path: '/m/blocked/quota',
    component: BlockedQuota
  },
  {
    path: '/m/blocked/duplicate',
    component: BlockedDuplicate,
    meta: {
      requiresLogin: true
    }
  },
  {
    path: '/m/review',
    component: ReviewSubmitted,
    meta: {
      requiresLogin: true
    }
  },
  {
    path: '/m/error',
    component: ErrorPage
  },
  {
    path: '/auth/sso/callback',
    component: SsoCallback
  },
  {
    path: '/auth/logout',
    component: LogoutPage
  },
  {
    path: '/local-admin/login',
    component: AdminLogin,
    meta: {
      guestOnly: true
    }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: {
      requiresAuth: true,
      requiresAdminAccess: true
    },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        component: Dashboard
      },
      {
        path: 'surveys',
        component: SurveyList
      },
      {
        path: 'surveys/new',
        component: CreateSurvey
      },
      {
        path: 'surveys/:id/edit',
        component: EditSurvey
      },
      {
        path: 'surveys/:id/stats',
        component: SurveyStats
      },
      {
        path: 'surveys/:id/auth',
        component: SurveyAuth,
        meta: {
          requiredPermission: 'survey:auth'
        }
      },
      {
        path: 'users',
        component: UserManagement
      },
      {
        path: 'permissions',
        component: PermissionManagement
      },
      {
        path: 'logs',
        component: LogManagement
      },
      {
        path: 'password',
        component: ChangePassword
      },
      {
        path: 'settings',
        component: SystemSettings,
        children: [
          {
            path: '',
            redirect: '/admin/settings/basic'
          },
          {
            path: 'basic',
            component: BasicSettings
          },
          {
            path: 'integration',
            component: ThirdPartyIntegration
          }
        ]
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

async function tryLoadPublicAuthSettings() {
  try {
    const response = await getPublicAuthSettings()
    if (response.code !== 20000 || !response.data?.authIntegration) {
      return null
    }

    return response.data.authIntegration
  } catch (error) {
    // keep route guard resilient when public settings endpoint is unavailable
    return null
  }
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const permissionStore = usePermissionStore()
  const settingsStore = useSettingsStore()
  const hasToken = !!authStore.token

  if ((to.meta.requiresAuth || to.meta.requiresLogin) && !hasToken) {
    const publicAuthIntegration = await tryLoadPublicAuthSettings()
    const settingsForOauth = publicAuthIntegration
      ? {
          ...settingsStore.settings,
          authIntegration: publicAuthIntegration
        }
      : settingsStore.settings

    if (shouldAutoThirdPartyForAdmin(settingsForOauth)) {
      const oauthResult = beginThirdPartyLogin(settingsForOauth, to.fullPath)

      if (oauthResult.ok) {
        window.location.href = oauthResult.url
        return false
      }
    }

    return buildLoginPathWithRedirect(to.fullPath)
  }

  if (to.meta.requiresAdminAccess && authStore.isRole1) {
    return '/m'
  }

  if (to.meta.guestOnly && hasToken) {
    return getPostLoginPath(authStore.role, to.query.redirect)
  }

  if (typeof to.meta.requiredPermission === 'string') {
    const role = authStore.role
    const isKnownRole = role === 'ROLE1' || role === 'ROLE2' || role === 'ROLE3'
    const rolePermissions = isKnownRole ? permissionStore.rolePermissionMap[role] || [] : []

    if (!rolePermissions.includes(to.meta.requiredPermission)) {
      return '/admin/surveys'
    }
  }
})

export default router
