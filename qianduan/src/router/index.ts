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
import BasicSettings from '../views/admin/settings/BasicSettings.vue'
import ThirdPartyIntegration from '../views/admin/settings/ThirdPartyIntegration.vue'
import { buildLoginPathWithRedirect, getPostLoginPath } from '../utils/auth-redirect'
import {
  beginThirdPartyLogin,
  shouldAutoThirdPartyForAdmin
} from '../utils/oauth-login'

const routes = [
  {
    path: '/',
    redirect: '/m'
  },
  {
    path: '/m',
    component: MobileHome
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
    component: SubmitSuccess
  },
  {
    path: '/m/blocked/quota',
    component: BlockedQuota
  },
  {
    path: '/m/blocked/duplicate',
    component: BlockedDuplicate
  },
  {
    path: '/m/review',
    component: ReviewSubmitted
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

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const permissionStore = usePermissionStore()
  const settingsStore = useSettingsStore()
  const hasToken = !!authStore.token

  if ((to.meta.requiresAuth || to.meta.requiresLogin) && !hasToken) {
    if (to.meta.requiresAuth && shouldAutoThirdPartyForAdmin(settingsStore.settings)) {
      const oauthResult = beginThirdPartyLogin(settingsStore.settings, to.fullPath)

      if (oauthResult.ok) {
        window.location.href = oauthResult.url
        next(false)
        return
      }
    }

    next(buildLoginPathWithRedirect(to.fullPath))
    return
  }

  if (to.meta.requiresAdminAccess && authStore.isRole1) {
    next('/m')
    return
  }

  if (to.meta.guestOnly && hasToken) {
    next(getPostLoginPath(authStore.role, to.query.redirect))
    return
  }

  if (typeof to.meta.requiredPermission === 'string') {
    const role = authStore.role
    const isKnownRole = role === 'ROLE1' || role === 'ROLE2' || role === 'ROLE3'
    const rolePermissions = isKnownRole ? permissionStore.rolePermissionMap[role] || [] : []

    if (!rolePermissions.includes(to.meta.requiredPermission)) {
      next('/admin/surveys')
      return
    }
  }

  next()
})

export default router
