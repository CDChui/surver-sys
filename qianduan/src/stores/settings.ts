import { defineStore } from 'pinia'

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'
export type OauthEnvironment = 'TEST' | 'PROD' | 'CUSTOM'
export type OauthProviderProtocol = 'IAM_TEMPLATE' | 'OAUTH2' | 'OIDC'
export type AuthLoginMode = 'LOCAL_ONLY' | 'LOCAL_AND_OAUTH' | 'OAUTH_ONLY'

export interface OauthSettings {
  enabled: boolean
  environment: OauthEnvironment
  authDomain: string
  clientId: string
  clientSecret: string
  redirectUri: string
  logoutRedirectUri: string
}

export interface OauthProviderConfig {
  id: string
  name: string
  protocol: OauthProviderProtocol
  enabled: boolean
  priority: number
  environment: OauthEnvironment
  authDomain: string
  clientId: string
  clientSecret: string
  scope: string
  redirectUri: string
  logoutRedirectUri: string
  authorizeUrl: string
  tokenUrl: string
  userInfoUrl: string
  refreshUrl: string
  revokeUrl: string
  userIdField: string
  realNameField: string
  emailField: string
}

export interface AuthIntegrationSettings {
  loginMode: AuthLoginMode
  defaultProviderId: string
  autoCreateUser: boolean
  defaultRole: UserRole
  providers: OauthProviderConfig[]
}

export interface SystemSettings {
  systemName: string
  systemDomain: string
  defaultPageSize: number
  enableLog: boolean
  enableResumeDraft: boolean
  allowDuplicateSubmit: boolean
  adminLogo: string
  userHomeLogo: string
  oauth: OauthSettings
  authIntegration: AuthIntegrationSettings
}

const STORAGE_KEY = 'SYSTEM_SETTINGS'

function formatDomainAsUrl(rawDomain: string) {
  const domain = String(rawDomain || '').trim().replace(/\/+$/, '')
  if (!domain) return ''
  if (/^https?:\/\//i.test(domain)) return domain
  return `https://${domain}`
}

function formatSystemDomainAsUrl(rawDomain: string) {
  const domain = String(rawDomain || '').trim().replace(/\/+$/, '')
  if (!domain) return ''
  if (/^https?:\/\//i.test(domain)) return domain
  const protocol =
    typeof window !== 'undefined' && window.location?.protocol
      ? window.location.protocol
      : 'https:'
  return `${protocol}//${domain}`
}

function trimText(value: unknown) {
  return String(value || '').trim()
}

function buildIamEndpoints(rawDomain: string) {
  const baseUrl = formatDomainAsUrl(rawDomain)

  return {
    authorizeUrl: baseUrl ? `${baseUrl}/idp/oauth2/authorize` : '',
    tokenUrl: baseUrl ? `${baseUrl}/idp/oauth2/getToken` : '',
    userInfoUrl: baseUrl ? `${baseUrl}/idp/oauth2/getUserInfo` : '',
    refreshUrl: baseUrl ? `${baseUrl}/idp/oauth2/refreshToken` : '',
    revokeUrl: baseUrl ? `${baseUrl}/idp/oauth2/revokeToken` : ''
  }
}

function buildIamDefaultUris(rawDomain: string) {
  const baseUrl = formatSystemDomainAsUrl(rawDomain)

  return {
    redirectUri: baseUrl ? `${baseUrl}/auth/sso/callback` : '',
    logoutRedirectUri: baseUrl ? `${baseUrl}/auth/logout` : ''
  }
}

function extractDomainFromUrl(rawUrl: string) {
  const text = trimText(rawUrl)
  if (!text) return ''

  try {
    const url = new URL(text)
    return url.host
  } catch (error) {
    return ''
  }
}

function getDefaultOauthSettings(): OauthSettings {
  return {
    enabled: false,
    environment: 'CUSTOM',
    authDomain: '',
    clientId: '',
    clientSecret: '',
    redirectUri: '',
    logoutRedirectUri: ''
  }
}

function getDefaultProvider(): OauthProviderConfig {
  const endpoints = buildIamEndpoints('')

  return {
    id: 'iam-default',
    name: '校园统一身份认证(IAM)',
    protocol: 'IAM_TEMPLATE',
    enabled: false,
    priority: 1,
    environment: 'CUSTOM',
    authDomain: '',
    clientId: '',
    clientSecret: '',
    scope: 'openid profile',
    redirectUri: '',
    logoutRedirectUri: '',
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

function getDefaultAuthIntegration(): AuthIntegrationSettings {
  const provider = getDefaultProvider()

  return {
    loginMode: 'LOCAL_ONLY',
    defaultProviderId: provider.id,
    autoCreateUser: true,
    defaultRole: 'ROLE1',
    providers: [provider]
  }
}

function getDefaultSettings(): SystemSettings {
  return {
    systemName: '问卷调查系统',
    systemDomain: '',
    defaultPageSize: 5,
    enableLog: true,
    enableResumeDraft: true,
    allowDuplicateSubmit: false,
    adminLogo: '',
    userHomeLogo: '',
    oauth: getDefaultOauthSettings(),
    authIntegration: getDefaultAuthIntegration()
  }
}

function normalizeEnvironment(value: unknown): OauthEnvironment {
  if (value === 'PROD') return 'PROD'
  if (value === 'CUSTOM') return 'CUSTOM'
  return 'TEST'
}

function normalizeProtocol(value: unknown): OauthProviderProtocol {
  if (value === 'OAUTH2') return 'OAUTH2'
  if (value === 'OIDC') return 'OIDC'
  return 'IAM_TEMPLATE'
}

function normalizeLoginMode(value: unknown): AuthLoginMode {
  if (value === 'LOCAL_AND_OAUTH') return 'LOCAL_AND_OAUTH'
  if (value === 'OAUTH_ONLY') return 'OAUTH_ONLY'
  return 'LOCAL_ONLY'
}

function normalizeRole(value: unknown): UserRole {
  if (value === 'ROLE2') return 'ROLE2'
  if (value === 'ROLE3') return 'ROLE3'
  return 'ROLE1'
}

function normalizeLogoValue(value: unknown): string {
  if (typeof value !== 'string') return ''
  return value.trim()
}

function normalizeProvider(
  providerRaw: unknown,
  fallbackIndex = 0,
  systemDomain = ''
): OauthProviderConfig {
  const defaults = getDefaultProvider()
  const candidate = (providerRaw || {}) as Partial<OauthProviderConfig>
  const protocol = normalizeProtocol(candidate.protocol)
  const rawEnvironment = normalizeEnvironment(candidate.environment)
  const environment = protocol === 'IAM_TEMPLATE' ? 'CUSTOM' : rawEnvironment
  const authDomain = trimText(candidate.authDomain)

  const endpoints =
    protocol === 'IAM_TEMPLATE'
      ? buildIamEndpoints(authDomain)
      : {
          authorizeUrl: trimText(candidate.authorizeUrl),
          tokenUrl: trimText(candidate.tokenUrl),
          userInfoUrl: trimText(candidate.userInfoUrl),
          refreshUrl: trimText(candidate.refreshUrl),
          revokeUrl: trimText(candidate.revokeUrl)
        }
  const defaultUris = buildIamDefaultUris(systemDomain)

  const normalizedProvider: OauthProviderConfig = {
    ...defaults,
    ...candidate,
    id: trimText(candidate.id) || `provider-${Date.now()}-${fallbackIndex + 1}`,
    name: trimText(candidate.name) || `认证平台${fallbackIndex + 1}`,
    protocol,
    enabled: Boolean(candidate.enabled),
    priority:
      typeof candidate.priority === 'number' && Number.isFinite(candidate.priority)
        ? candidate.priority
        : fallbackIndex + 1,
    environment,
    authDomain,
    clientId: trimText(candidate.clientId),
    clientSecret: trimText(candidate.clientSecret),
    scope: trimText(candidate.scope),
    redirectUri:
      protocol === 'IAM_TEMPLATE'
        ? trimText(candidate.redirectUri) || defaultUris.redirectUri
        : trimText(candidate.redirectUri),
    logoutRedirectUri:
      protocol === 'IAM_TEMPLATE'
        ? trimText(candidate.logoutRedirectUri) || defaultUris.logoutRedirectUri
        : trimText(candidate.logoutRedirectUri),
    authorizeUrl: endpoints.authorizeUrl,
    tokenUrl: endpoints.tokenUrl,
    userInfoUrl: endpoints.userInfoUrl,
    refreshUrl: endpoints.refreshUrl,
    revokeUrl: endpoints.revokeUrl,
    userIdField: trimText(candidate.userIdField) || defaults.userIdField,
    realNameField: trimText(candidate.realNameField) || defaults.realNameField,
    emailField: trimText(candidate.emailField) || defaults.emailField
  }

  if (
    normalizedProvider.protocol === 'IAM_TEMPLATE' &&
    normalizedProvider.userIdField === 'uid'
  ) {
    normalizedProvider.userIdField = 'employeeNumber'
  }

  return normalizedProvider
}

function migrateLegacyOauthToIntegration(
  oauthRaw: Partial<OauthSettings> | undefined,
  systemDomain = ''
): AuthIntegrationSettings {
  const defaults = getDefaultAuthIntegration()
  const provider = normalizeProvider(
    {
      ...defaults.providers[0],
      enabled: Boolean(oauthRaw?.enabled),
      environment: 'CUSTOM',
      authDomain: trimText(oauthRaw?.authDomain),
      clientId: trimText(oauthRaw?.clientId),
      clientSecret: trimText(oauthRaw?.clientSecret),
      redirectUri: trimText(oauthRaw?.redirectUri),
      logoutRedirectUri: trimText(oauthRaw?.logoutRedirectUri)
    },
    0,
    systemDomain
  )

  return {
    ...defaults,
    loginMode: provider.enabled ? 'LOCAL_AND_OAUTH' : 'LOCAL_ONLY',
    defaultProviderId: provider.id,
    providers: [provider]
  }
}

function normalizeAuthIntegration(
  integrationRaw: unknown,
  legacyOauth: OauthSettings,
  systemDomain = ''
): AuthIntegrationSettings {
  const defaults = getDefaultAuthIntegration()
  const candidate = (integrationRaw || {}) as Partial<AuthIntegrationSettings>
  const rawProviders = Array.isArray(candidate.providers) ? candidate.providers : []

  if (rawProviders.length === 0) {
    return migrateLegacyOauthToIntegration(legacyOauth, systemDomain)
  }

  const providers = rawProviders.map((item, index) =>
    normalizeProvider(item, index, systemDomain)
  )
  const providerMap = new Map(providers.map((item) => [item.id, item]))
  const normalizedDefaultProviderId = trimText(candidate.defaultProviderId)
  const fallbackProviderId = providers[0]?.id || defaults.providers[0]?.id || 'iam-default'
  const defaultProviderId = providerMap.has(normalizedDefaultProviderId)
    ? normalizedDefaultProviderId
    : fallbackProviderId

  return {
    ...defaults,
    ...candidate,
    loginMode: normalizeLoginMode(candidate.loginMode),
    defaultProviderId,
    autoCreateUser: Boolean(candidate.autoCreateUser),
    defaultRole: normalizeRole(candidate.defaultRole),
    providers
  }
}

export function convertAuthIntegrationToLegacyOauth(
  integration: AuthIntegrationSettings
): OauthSettings {
  const defaults = getDefaultOauthSettings()
  const providers = Array.isArray(integration.providers) ? integration.providers : []
  const defaultProvider =
    providers.find((item) => item.id === integration.defaultProviderId) || providers[0]

  if (!defaultProvider) {
    return defaults
  }

  const enabled =
    integration.loginMode !== 'LOCAL_ONLY' &&
    defaultProvider.enabled &&
    Boolean(defaultProvider.clientId) &&
    Boolean(defaultProvider.clientSecret)

  const authDomain =
    defaultProvider.protocol === 'IAM_TEMPLATE'
      ? defaultProvider.authDomain
      : defaultProvider.authDomain || extractDomainFromUrl(defaultProvider.authorizeUrl)

  return {
    enabled,
    environment: 'CUSTOM',
    authDomain,
    clientId: trimText(defaultProvider.clientId),
    clientSecret: trimText(defaultProvider.clientSecret),
    redirectUri: trimText(defaultProvider.redirectUri),
    logoutRedirectUri: trimText(defaultProvider.logoutRedirectUri)
  }
}

function normalizeSettings(rawSettings: unknown): SystemSettings {
  const defaults = getDefaultSettings()
  const candidate = (rawSettings || {}) as Partial<SystemSettings>
  const legacyOauth = {
    ...defaults.oauth,
    ...(candidate.oauth || {})
  }
  const systemDomain = trimText(candidate.systemDomain) || defaults.systemDomain
  const authIntegration = normalizeAuthIntegration(
    candidate.authIntegration,
    legacyOauth,
    systemDomain
  )

  return {
    ...defaults,
    ...candidate,
    adminLogo: normalizeLogoValue(candidate.adminLogo),
    userHomeLogo: normalizeLogoValue(candidate.userHomeLogo),
    oauth: convertAuthIntegrationToLegacyOauth(authIntegration),
    authIntegration
  }
}

function ensureStorage() {
  if (!localStorage.getItem(STORAGE_KEY)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(getDefaultSettings()))
  }
}

function readSettings(): SystemSettings {
  ensureStorage()
  const rawSettings = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  return normalizeSettings(rawSettings)
}

function writeSettings(settings: SystemSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
}

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    settings: readSettings() as SystemSettings
  }),

  actions: {
    refreshFromStorage() {
      this.settings = readSettings()
    },

    saveSettings(newSettings: SystemSettings) {
      this.settings = normalizeSettings(newSettings)
      writeSettings(this.settings)
    }
  }
})
