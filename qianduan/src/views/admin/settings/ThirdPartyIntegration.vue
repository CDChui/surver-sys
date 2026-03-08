<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { getSystemSettings, saveSystemSettings } from '../../../api/settings'
import {
  convertAuthIntegrationToLegacyOauth,
  useSettingsStore,
  type AuthIntegrationSettings,
  type AuthLoginMode,
  type OauthProviderConfig,
  type OauthProviderProtocol,
  type UserRole
} from '../../../stores/settings'
import { appendOperationLog } from '../../../utils/log'

const settingsStore = useSettingsStore()

const loginModeOptions: Array<{ label: string; value: AuthLoginMode }> = [
  { label: '仅本地登录', value: 'LOCAL_ONLY' },
  { label: '本地 + 第三方登录', value: 'LOCAL_AND_OAUTH' },
  { label: '仅第三方登录', value: 'OAUTH_ONLY' }
]

const protocolOptions: Array<{ label: string; value: OauthProviderProtocol }> = [
  { label: 'IAM 模板', value: 'IAM_TEMPLATE' },
  { label: 'OAuth2 通用授权码', value: 'OAUTH2' },
  { label: 'OIDC', value: 'OIDC' }
]

const roleOptions: Array<{ label: string; value: UserRole }> = [
  { label: '普通用户（ROLE1）', value: 'ROLE1' },
  { label: '业务管理员（ROLE2）', value: 'ROLE2' },
  { label: '系统管理员（ROLE3）', value: 'ROLE3' }
]

const providerDialogVisible = ref(false)
const editingProviderId = ref('')

const form = reactive<AuthIntegrationSettings>(
  cloneIntegration(settingsStore.settings.authIntegration)
)

const providerForm = reactive<OauthProviderConfig>(createProviderTemplate())

const providerList = computed(() =>
  [...form.providers].sort((a, b) => a.priority - b.priority)
)

const providerSelectionOptions = computed(() =>
  form.providers.filter((item) => item.enabled)
)

const providerUrlPreview = computed(() => {
  if (providerForm.protocol === 'IAM_TEMPLATE') {
    return buildIamEndpointMap(providerForm.authDomain)
  }

  return {
    authorizeUrl: providerForm.authorizeUrl.trim(),
    tokenUrl: providerForm.tokenUrl.trim(),
    userInfoUrl: providerForm.userInfoUrl.trim(),
    refreshUrl: providerForm.refreshUrl.trim(),
    revokeUrl: providerForm.revokeUrl.trim()
  }
})

function createProviderId() {
  return `provider-${Date.now()}-${Math.floor(Math.random() * 100000)}`
}

function formatDomainAsUrl(rawDomain: string) {
  const domain = String(rawDomain || '').trim().replace(/\/+$/, '')
  if (!domain) return ''
  if (/^https?:\/\//i.test(domain)) return domain
  return `https://${domain}`
}

function buildIamEndpointMap(rawDomain: string) {
  const baseUrl = formatDomainAsUrl(rawDomain)

  return {
    authorizeUrl: baseUrl ? `${baseUrl}/idp/oauth2/authorize` : '',
    tokenUrl: baseUrl ? `${baseUrl}/idp/oauth2/getToken` : '',
    userInfoUrl: baseUrl ? `${baseUrl}/idp/oauth2/getUserInfo` : '',
    refreshUrl: baseUrl ? `${baseUrl}/idp/oauth2/refreshToken` : '',
    revokeUrl: baseUrl ? `${baseUrl}/idp/oauth2/revokeToken` : ''
  }
}

function cloneIntegration(source: AuthIntegrationSettings): AuthIntegrationSettings {
  return {
    loginMode: source.loginMode,
    defaultProviderId: source.defaultProviderId,
    autoCreateUser: source.autoCreateUser,
    defaultRole: source.defaultRole,
    providers: source.providers.map((item) => ({ ...item }))
  }
}

function createProviderTemplate(): OauthProviderConfig {
  const endpoints = buildIamEndpointMap('')
  const defaultUris = getDefaultIamUris()

  return {
    id: createProviderId(),
    name: '',
    protocol: 'IAM_TEMPLATE',
    enabled: true,
    priority: form.providers.length + 1,
    environment: 'CUSTOM',
    authDomain: '',
    clientId: '',
    clientSecret: '',
    scope: 'openid profile',
    redirectUri: defaultUris.redirectUri,
    logoutRedirectUri: defaultUris.logoutRedirectUri,
    authorizeUrl: endpoints.authorizeUrl,
    tokenUrl: endpoints.tokenUrl,
    userInfoUrl: endpoints.userInfoUrl,
    refreshUrl: endpoints.refreshUrl,
    revokeUrl: endpoints.revokeUrl,
    userIdField: 'employeeNumber',
    realNameField: 'displayName',
    emailField: 'mail'
  }
}

function getProtocolText(protocol: OauthProviderProtocol) {
  if (protocol === 'IAM_TEMPLATE') return 'IAM 模板'
  if (protocol === 'OIDC') return 'OIDC'
  return 'OAuth2'
}

function getModeText(mode: AuthLoginMode) {
  if (mode === 'LOCAL_AND_OAUTH') return '本地 + 第三方登录'
  if (mode === 'OAUTH_ONLY') return '仅第三方登录'
  return '仅本地登录'
}

function normalizeProvider(candidate: OauthProviderConfig) {
  const normalized: OauthProviderConfig = {
    ...candidate,
    name: candidate.name.trim(),
    clientId: candidate.clientId.trim(),
    clientSecret: candidate.clientSecret.trim(),
    scope: candidate.scope.trim(),
    authDomain: candidate.authDomain.trim(),
    redirectUri: candidate.redirectUri.trim(),
    logoutRedirectUri: candidate.logoutRedirectUri.trim(),
    authorizeUrl: candidate.authorizeUrl.trim(),
    tokenUrl: candidate.tokenUrl.trim(),
    userInfoUrl: candidate.userInfoUrl.trim(),
    refreshUrl: candidate.refreshUrl.trim(),
    revokeUrl: candidate.revokeUrl.trim(),
    userIdField: candidate.userIdField.trim(),
    realNameField: candidate.realNameField.trim(),
    emailField: candidate.emailField.trim(),
    priority:
      typeof candidate.priority === 'number' && Number.isFinite(candidate.priority)
        ? candidate.priority
        : 1
  }

  if (normalized.protocol !== 'IAM_TEMPLATE') return normalized

  normalized.environment = 'CUSTOM'

  const iamEndpoints = buildIamEndpointMap(normalized.authDomain)
  normalized.authorizeUrl = iamEndpoints.authorizeUrl
  normalized.tokenUrl = iamEndpoints.tokenUrl
  normalized.userInfoUrl = iamEndpoints.userInfoUrl
  normalized.refreshUrl = iamEndpoints.refreshUrl
  normalized.revokeUrl = iamEndpoints.revokeUrl

  return normalized
}

function isHttpUrl(text: string) {
  return /^https?:\/\//i.test(text.trim())
}

function validateProvider(provider: OauthProviderConfig) {
  if (!provider.name.trim()) return '认证平台名称不能为空'

  if (provider.redirectUri && !isHttpUrl(provider.redirectUri)) {
    return '回调地址 redirect_uri 需以 http:// 或 https:// 开头'
  }

  if (provider.logoutRedirectUri && !isHttpUrl(provider.logoutRedirectUri)) {
    return '退出回跳地址需以 http:// 或 https:// 开头'
  }

  if (!provider.enabled) return ''

  if (!provider.clientId.trim()) return `${provider.name}: client_id 不能为空`
  if (!provider.clientSecret.trim()) return `${provider.name}: client_secret 不能为空`
  if (!provider.redirectUri.trim()) return `${provider.name}: redirect_uri 不能为空`
  if (!isHttpUrl(provider.redirectUri)) {
    return `${provider.name}: redirect_uri 需以 http:// 或 https:// 开头`
  }

  if (provider.protocol === 'IAM_TEMPLATE') {
    if (!provider.authDomain.trim()) return `${provider.name}: IAM 认证域名不能为空`
    return ''
  }

  if (!provider.authorizeUrl.trim()) return `${provider.name}: authorize_url 不能为空`
  if (!provider.tokenUrl.trim()) return `${provider.name}: token_url 不能为空`
  if (!provider.userInfoUrl.trim()) return `${provider.name}: userinfo_url 不能为空`

  if (!isHttpUrl(provider.authorizeUrl)) {
    return `${provider.name}: authorize_url 需以 http:// 或 https:// 开头`
  }

  if (!isHttpUrl(provider.tokenUrl)) {
    return `${provider.name}: token_url 需以 http:// 或 https:// 开头`
  }

  if (!isHttpUrl(provider.userInfoUrl)) {
    return `${provider.name}: userinfo_url 需以 http:// 或 https:// 开头`
  }

  if (provider.refreshUrl && !isHttpUrl(provider.refreshUrl)) {
    return `${provider.name}: refresh_url 需以 http:// 或 https:// 开头`
  }

  if (provider.revokeUrl && !isHttpUrl(provider.revokeUrl)) {
    return `${provider.name}: revoke_url 需以 http:// 或 https:// 开头`
  }

  return ''
}

function ensureDefaultProvider() {
  const enabledProvider = form.providers.find((item) => item.enabled)

  if (!enabledProvider) {
    form.defaultProviderId = form.providers[0]?.id || ''
    return
  }

  const isCurrentDefaultEnabled = form.providers.some(
    (item) => item.id === form.defaultProviderId && item.enabled
  )

  if (!isCurrentDefaultEnabled) {
    form.defaultProviderId = enabledProvider.id
  }
}

function openCreateProviderDialog() {
  editingProviderId.value = ''
  Object.assign(providerForm, createProviderTemplate())
  providerDialogVisible.value = true
}

function openEditProviderDialog(provider: OauthProviderConfig) {
  editingProviderId.value = provider.id
  const nextProvider = { ...provider }

  if (nextProvider.protocol === 'IAM_TEMPLATE') {
    const defaults = getDefaultIamUris()
    if (!nextProvider.redirectUri.trim()) {
      nextProvider.redirectUri = defaults.redirectUri
    }
    if (!nextProvider.logoutRedirectUri.trim()) {
      nextProvider.logoutRedirectUri = defaults.logoutRedirectUri
    }
  }

  Object.assign(providerForm, nextProvider)
  providerDialogVisible.value = true
}

function handleDuplicateProvider(provider: OauthProviderConfig) {
  const nextProvider = normalizeProvider({
    ...provider,
    id: createProviderId(),
    enabled: false,
    name: `${provider.name}-副本`,
    priority: form.providers.length + 1
  })
  form.providers.push(nextProvider)
  ensureDefaultProvider()
}

function handleDeleteProvider(provider: OauthProviderConfig) {
  const accepted = window.confirm(`确认删除认证平台“${provider.name}”？`)
  if (!accepted) return

  const index = form.providers.findIndex((item) => item.id === provider.id)
  if (index < 0) return
  form.providers.splice(index, 1)
  ensureDefaultProvider()
}

function handleSaveProviderDialog() {
  const normalized = normalizeProvider({ ...providerForm })
  const message = validateProvider(normalized)

  if (message) {
    alert(message)
    return
  }

  if (editingProviderId.value) {
    const index = form.providers.findIndex((item) => item.id === editingProviderId.value)
    if (index >= 0) {
      form.providers[index] = normalized
    }
  } else {
    form.providers.push(normalized)
  }

  providerDialogVisible.value = false
  ensureDefaultProvider()
}

function handleValidateProvider(provider: OauthProviderConfig) {
  const message = validateProvider(normalizeProvider(provider))
  if (message) {
    alert(message)
    return
  }

  alert(`平台“${provider.name}”配置校验通过`)
}

function validateIntegration(integration: AuthIntegrationSettings) {
  if (integration.providers.length === 0) {
    if (integration.loginMode === 'LOCAL_ONLY') return ''
    return '当前登录模式要求至少配置 1 个认证平台'
  }

  const enabledProviders = integration.providers.filter((item) => item.enabled)

  if (integration.loginMode !== 'LOCAL_ONLY' && enabledProviders.length === 0) {
    return '当前登录模式要求至少启用 1 个认证平台'
  }

  if (integration.loginMode !== 'LOCAL_ONLY') {
    const hasDefaultEnabled = enabledProviders.some(
      (item) => item.id === integration.defaultProviderId
    )
    if (!hasDefaultEnabled) {
      return '默认认证平台必须是已启用状态'
    }
  }

  for (const provider of integration.providers) {
    const message = validateProvider(provider)
    if (message) return message
  }

  return ''
}

function normalizeIntegrationForSave() {
  const providers = form.providers.map((item) => normalizeProvider(item))
  const integration: AuthIntegrationSettings = {
    ...form,
    providers
  }

  const enabledProvider = providers.find((item) => item.enabled)
  const defaultProviderExists = providers.some(
    (item) => item.id === integration.defaultProviderId
  )
  const defaultProviderEnabled = providers.some(
    (item) => item.id === integration.defaultProviderId && item.enabled
  )

  if (!defaultProviderExists) {
    integration.defaultProviderId = enabledProvider?.id || providers[0]?.id || ''
  } else if (integration.loginMode !== 'LOCAL_ONLY' && !defaultProviderEnabled) {
    integration.defaultProviderId = enabledProvider?.id || ''
  }

  return integration
}

function getDefaultIamUris() {
  const domainBase = formatDomainAsUrl(settingsStore.settings.systemDomain)

  return {
    redirectUri: domainBase ? `${domainBase}/auth/sso/callback` : '',
    logoutRedirectUri: domainBase ? `${domainBase}/auth/logout` : ''
  }
}

async function handleValidateAll() {
  const integration = normalizeIntegrationForSave()
  const message = validateIntegration(integration)

  if (message) {
    alert(message)
    return
  }

  alert('第三方认证配置校验通过')
}

async function handleSave() {
  const integration = normalizeIntegrationForSave()
  const message = validateIntegration(integration)

  if (message) {
    alert(message)
    return
  }

  const payload = {
    ...settingsStore.settings,
    authIntegration: integration,
    oauth: convertAuthIntegrationToLegacyOauth(integration)
  }

  try {
    const response = await saveSystemSettings(payload)

    if (response.code !== 20000) {
      alert(response.message || '第三方对接设置保存失败')
      return
    }

    appendOperationLog({
      module: 'SYSTEM',
      action: 'UPDATE',
      target: `第三方对接设置（${getModeText(integration.loginMode)}）`
    })

    alert('第三方对接设置已保存')
  } catch (error) {
    alert('第三方对接设置保存失败')
  }
}

async function handleReset() {
  try {
    const response = await getSystemSettings()

    if (response.code !== 20000) {
      alert(response.message || '读取系统设置失败')
      return
    }

    const next = cloneIntegration(response.data.authIntegration)
    form.loginMode = next.loginMode
    form.defaultProviderId = next.defaultProviderId
    form.autoCreateUser = next.autoCreateUser
    form.defaultRole = next.defaultRole
    form.providers.splice(0, form.providers.length, ...next.providers)

    alert('已重置为当前保存配置')
  } catch (error) {
    alert('读取系统设置失败')
  }
}
</script>

<template>
  <div style="max-width: 1100px;">
    <div style="font-size: 16px; font-weight: 700; margin-bottom: 16px;">
      第三方对接（通用 OAuth）
    </div>

    <el-card style="margin-bottom: 16px;">
      <template #header>
        <div style="font-weight: 700;">全局认证设置</div>
      </template>

      <div
        style="
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 16px;
        "
      >
        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">登录模式</div>
          <el-select v-model="form.loginMode" style="width: 100%;">
            <el-option
              v-for="item in loginModeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">默认认证平台</div>
          <el-select
            v-model="form.defaultProviderId"
            style="width: 100%;"
            :disabled="form.loginMode === 'LOCAL_ONLY'"
            placeholder="请选择默认认证平台"
          >
            <el-option
              v-for="item in providerSelectionOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">首次登录自动创建用户</div>
          <el-switch v-model="form.autoCreateUser" />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">新用户默认角色</div>
          <el-select v-model="form.defaultRole" style="width: 100%;">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
          "
        >
          <div style="font-weight: 700;">认证平台列表</div>
          <el-button type="primary" @click="openCreateProviderDialog">
            新增认证平台
          </el-button>
        </div>
      </template>

      <el-table :data="providerList" style="width: 100%;">
        <el-table-column prop="name" label="平台名称" min-width="180" />

        <el-table-column label="协议" min-width="130">
          <template #default="{ row }">
            {{ getProtocolText(row.protocol) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="priority" label="优先级" min-width="90" />

        <el-table-column prop="redirectUri" label="回调地址" min-width="260">
          <template #default="{ row }">
            {{ row.redirectUri || '-' }}
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <div style="display: flex; gap: 8px; flex-wrap: wrap;">
              <el-button type="primary" link @click="openEditProviderDialog(row)">
                编辑
              </el-button>

              <el-button type="primary" link @click="handleDuplicateProvider(row)">
                复制
              </el-button>

              <el-button type="primary" link @click="handleValidateProvider(row)">
                校验
              </el-button>

              <el-button type="danger" link @click="handleDeleteProvider(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div style="display: flex; gap: 12px; margin-top: 16px;">
      <el-button type="primary" @click="handleSave">
        保存第三方对接设置
      </el-button>

      <el-button @click="handleValidateAll">
        校验配置
      </el-button>

      <el-button @click="handleReset">
        重置
      </el-button>
    </div>

    <el-dialog
      v-model="providerDialogVisible"
      :title="editingProviderId ? '编辑认证平台' : '新增认证平台'"
      width="920px"
    >
      <div
        style="
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 16px;
        "
      >
        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">平台名称</div>
          <el-input v-model="providerForm.name" placeholder="例如：校园统一身份认证" />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">协议类型</div>
          <el-select v-model="providerForm.protocol" style="width: 100%;">
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">是否启用</div>
          <el-switch v-model="providerForm.enabled" />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">优先级（数字越小越优先）</div>
          <el-input-number
            v-model="providerForm.priority"
            :min="1"
            :max="9999"
            style="width: 100%;"
          />
        </div>

        <template v-if="providerForm.protocol === 'IAM_TEMPLATE'">
          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">IAM 认证域名</div>
            <el-input
              v-model="providerForm.authDomain"
              placeholder="例如：auth.sztu.edu.cn"
            />
          </div>
        </template>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">client_id</div>
          <el-input
            v-model="providerForm.clientId"
            placeholder="请输入 OAuth 客户端 ID"
          />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">client_secret</div>
          <el-input
            v-model="providerForm.clientSecret"
            type="password"
            show-password
            placeholder="请输入 OAuth 客户端密钥"
          />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">scope</div>
          <el-input v-model="providerForm.scope" placeholder="例如：openid profile email" />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">用户唯一标识字段</div>
          <el-input
            v-model="providerForm.userIdField"
            placeholder="例如：employeeNumber（学工号）或 sub"
          />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">姓名字段</div>
          <el-input v-model="providerForm.realNameField" placeholder="例如：displayName 或 name" />
        </div>

        <div>
          <div style="margin-bottom: 8px; font-weight: 600;">邮箱字段</div>
          <el-input v-model="providerForm.emailField" placeholder="例如：mail 或 email" />
        </div>

        <div style="grid-column: 1 / -1;">
          <div style="margin-bottom: 8px; font-weight: 600;">回调地址 redirect_uri</div>
          <el-input
            v-model="providerForm.redirectUri"
            placeholder="例如：https://survey.example.com/auth/sso/callback"
          />
        </div>

        <div style="grid-column: 1 / -1;">
          <div style="margin-bottom: 8px; font-weight: 600;">退出回跳地址</div>
          <el-input
            v-model="providerForm.logoutRedirectUri"
            placeholder="例如：https://survey.example.com/auth/logout"
          />
        </div>

        <template v-if="providerForm.protocol !== 'IAM_TEMPLATE'">
          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">authorize_url</div>
            <el-input
              v-model="providerForm.authorizeUrl"
              placeholder="例如：https://auth.example.com/oauth2/authorize"
            />
          </div>

          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">token_url</div>
            <el-input
              v-model="providerForm.tokenUrl"
              placeholder="例如：https://auth.example.com/oauth2/token"
            />
          </div>

          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">userinfo_url</div>
            <el-input
              v-model="providerForm.userInfoUrl"
              placeholder="例如：https://auth.example.com/oauth2/userinfo"
            />
          </div>

          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">refresh_url（可选）</div>
            <el-input
              v-model="providerForm.refreshUrl"
              placeholder="例如：https://auth.example.com/oauth2/refresh"
            />
          </div>

          <div style="grid-column: 1 / -1;">
            <div style="margin-bottom: 8px; font-weight: 600;">revoke_url（可选）</div>
            <el-input
              v-model="providerForm.revokeUrl"
              placeholder="例如：https://auth.example.com/oauth2/revoke"
            />
          </div>
        </template>
      </div>

      <el-card style="margin-top: 16px;">
        <template #header>
          <div style="font-weight: 700;">接口地址预览</div>
        </template>

        <div style="display: grid; gap: 10px; font-size: 13px;">
          <div><b>authorize：</b>{{ providerUrlPreview.authorizeUrl || '-' }}</div>
          <div><b>token：</b>{{ providerUrlPreview.tokenUrl || '-' }}</div>
          <div><b>userinfo：</b>{{ providerUrlPreview.userInfoUrl || '-' }}</div>
          <div><b>refresh：</b>{{ providerUrlPreview.refreshUrl || '-' }}</div>
          <div><b>revoke：</b>{{ providerUrlPreview.revokeUrl || '-' }}</div>
        </div>
      </el-card>

      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <el-button @click="providerDialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" @click="handleSaveProviderDialog">
            保存平台
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
