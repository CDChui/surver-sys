<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSurveyDetail } from '../../api/survey'
import {
  addSurveyAuthUser,
  getSurveyAuthUsers,
  removeSurveyAuthUser
} from '../../api/survey-auth'
import { getUserList, type UserItemResult } from '../../api/user'
import { useSurveyAuthStore } from '../../stores/survey-auth'

const route = useRoute()
const router = useRouter()
const surveyAuthStore = useSurveyAuthStore()

const surveyId = computed(() => Number(route.params.id))

const loading = ref(false)
const keyword = ref('')

const surveyTitle = ref('')
const surveyDescription = ref('')

const businessAdminList = ref<UserItemResult[]>([])
const authorizedUsers = ref<
  {
    userId: number
    username: string
    realName: string
  }[]
>([])

const filteredBusinessAdmins = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return businessAdminList.value.filter((item) => {
    const notAuthorized = !authorizedUsers.value.some(
      (authUser) => authUser.userId === item.id
    )

    const matchKeyword =
      !text ||
      item.username.toLowerCase().includes(text) ||
      item.realName.toLowerCase().includes(text)

    return notAuthorized && matchKeyword
  })
})

async function loadSurveyAuthPage() {
  try {
    loading.value = true

    const [detailResponse, userResponse, authResponse] = await Promise.all([
      getSurveyDetail(surveyId.value),
      getUserList(),
      getSurveyAuthUsers(surveyId.value)
    ])

    if (detailResponse.code !== 20000) {
      alert(detailResponse.message || '加载问卷授权页失败')
      return
    }

    if (userResponse.code !== 20000) {
      alert(userResponse.message || '加载用户列表失败')
      return
    }

    if (authResponse.code !== 20000) {
      alert(authResponse.message || '加载授权列表失败')
      return
    }

    surveyTitle.value = detailResponse.data.title
    surveyDescription.value = detailResponse.data.description

    businessAdminList.value = userResponse.data.filter(
      (item) => item.role === 'ROLE2' && item.status === 'ENABLED'
    )

    authorizedUsers.value = authResponse.data
  } catch (error) {
    alert('加载问卷授权页失败')
  } finally {
    loading.value = false
  }
}

async function handleAuthorize(user: UserItemResult) {
  try {
    const response = await addSurveyAuthUser(surveyId.value, {
      userId: user.id,
      username: user.username,
      realName: user.realName
    })

    if (response.code !== 20000) {
      alert(response.message || '授权失败')
      return
    }

    surveyAuthStore.addUserToSurvey(surveyId.value, {
      userId: user.id,
      username: user.username,
      realName: user.realName
    })

    const reloadResponse = await getSurveyAuthUsers(surveyId.value)
    if (reloadResponse.code === 20000) {
      authorizedUsers.value = reloadResponse.data
    }

    alert(`已授权 ${user.realName} 管理当前问卷`)
  } catch (error) {
    alert('授权失败')
  }
}

async function handleRevoke(userId: number, realName: string) {
  const ok = window.confirm(`确定撤销 ${realName} 对当前问卷的管理权限吗？`)
  if (!ok) return

  try {
    const response = await removeSurveyAuthUser(surveyId.value, userId)

    if (response.code !== 20000) {
      alert(response.message || '撤销授权失败')
      return
    }

    surveyAuthStore.removeUserFromSurvey(surveyId.value, userId)

    const reloadResponse = await getSurveyAuthUsers(surveyId.value)
    if (reloadResponse.code === 20000) {
      authorizedUsers.value = reloadResponse.data
    }

    alert(`已撤销 ${realName} 的问卷管理权限`)
  } catch (error) {
    alert('撤销授权失败')
  }
}

async function handleReset() {
  keyword.value = ''

  const response = await getSurveyAuthUsers(surveyId.value)
  if (response.code === 20000) {
    authorizedUsers.value = response.data
  }

  alert('已恢复为当前保存的授权状态')
}

function handleBack() {
  router.push('/admin/surveys')
}

onMounted(() => {
  void loadSurveyAuthPage()
})
</script>

<template>
  <div>
    <el-card v-loading="loading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <h2 style="margin: 0;">问卷授权管理</h2>
            <p style="margin: 8px 0 0; color: #666;">
              当前问卷 ID：{{ surveyId }}
            </p>
          </div>

          <el-button @click="handleBack">
            返回问卷列表
          </el-button>
        </div>
      </template>

      <div style="margin-bottom: 24px;">
        <h3 style="margin: 0 0 12px;">{{ surveyTitle }}</h3>
        <p style="margin: 0; color: #666; line-height: 1.8;">
          {{ surveyDescription }}
        </p>
      </div>

      <el-card style="margin-bottom: 20px;">
        <template #header>
          <div
            style="
              display: flex;
              justify-content: space-between;
              align-items: center;
              gap: 12px;
              flex-wrap: wrap;
            "
          >
            <div style="font-weight: 700;">搜索可授权的业务管理员</div>

            <div style="display: flex; gap: 12px; align-items: center;">
              <el-input
                v-model="keyword"
                placeholder="请输入用户名或姓名"
                clearable
                style="width: 260px;"
              />
              <el-button @click="handleReset">重置</el-button>
            </div>
          </div>
        </template>

        <el-table :data="filteredBusinessAdmins" style="width: 100%">
          <el-table-column prop="id" label="用户ID" width="100" />
          <el-table-column prop="username" label="用户名" width="180" />
          <el-table-column prop="realName" label="姓名" width="180" />
          <el-table-column label="角色" width="140">
            <template #default>
              业务管理员
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button type="primary" size="small" @click="handleAuthorize(scope.row)">
                授权
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div
          v-if="filteredBusinessAdmins.length === 0"
          style="padding: 16px 0 0; text-align: center; color: #999;"
        >
          当前没有可授权的业务管理员
        </div>
      </el-card>

      <el-card>
        <template #header>
          <div style="font-weight: 700;">当前已授权的业务管理员</div>
        </template>

        <el-table :data="authorizedUsers" style="width: 100%">
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="username" label="用户名" width="180" />
          <el-table-column prop="realName" label="姓名" width="180" />
          <el-table-column label="角色" width="140">
            <template #default>
              业务管理员
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button
                type="danger"
                size="small"
                @click="handleRevoke(scope.row.userId, scope.row.realName)"
              >
                撤销
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div
          v-if="authorizedUsers.length === 0"
          style="padding: 16px 0 0; text-align: center; color: #999;"
        >
          当前还没有已授权的业务管理员
        </div>
      </el-card>
    </el-card>
  </div>
</template>