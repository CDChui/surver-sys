import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import MobileHome from '../views/mobile/Home.vue'
import SurveyPage from '../views/mobile/Survey.vue'
import SubmitSuccess from '../views/mobile/SubmitSuccess.vue'
import BlockedQuota from '../views/mobile/BlockedQuota.vue'
import BlockedDuplicate from '../views/mobile/BlockedDuplicate.vue'
import ReviewSubmitted from '../views/mobile/ReviewSubmitted.vue'
import ErrorPage from '../views/mobile/ErrorPage.vue'
import SsoCallback from '../views/auth/SsoCallback.vue'
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
    component: SurveyPage
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
        component: SurveyAuth
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
        component: SystemSettings
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
  const hasToken = !!authStore.token

  if (to.meta.requiresAuth && !hasToken) {
    next('/local-admin/login')
    return
  }

  if (to.meta.requiresAdminAccess && authStore.isRole1) {
    next('/m')
    return
  }

  if (to.meta.guestOnly && hasToken) {
    if (authStore.isRole1) {
      next('/m')
      return
    }

    next('/admin/dashboard')
    return
  }

  next()
})

export default router