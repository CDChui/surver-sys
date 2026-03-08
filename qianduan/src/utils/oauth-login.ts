import type { OauthProviderConfig, SystemSettings } from '../stores/settings'
import { sanitizeRedirectPath } from './auth-redirect'

interface OauthLoginState {
  nonce: string
  providerId: string
  redirectPath: string
  createdAt: number
}

const OAUTH_LOGIN_STATE_PREFIX = 'OAUTH_LOGIN_STATE_'
const OAUTH_LOGIN_STATE_TTL = 10 * 60 * 1000

function createNonce() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function normalizeProviders(settings: SystemSettings) {
  const providers = settings.authIntegration.providers || []
  return providers.filter((item) => item.enabled)
}

export function getDefaultEnabledProvider(settings: SystemSettings) {
  const providers = normalizeProviders(settings)
  if (providers.length === 0) return null

  const byDefaultId = providers.find(
    (item) => item.id === settings.authIntegration.defaultProviderId
  )

  return byDefaultId || providers[0] || null
}

export function shouldAutoThirdPartyForAdmin(settings: SystemSettings) {
  if (settings.authIntegration.loginMode === 'LOCAL_ONLY') return false

  const provider = getDefaultEnabledProvider(settings)
  if (!provider) return false

  return Boolean(
    provider.authorizeUrl.trim() &&
      provider.clientId.trim() &&
      provider.redirectUri.trim()
  )
}

export function buildAuthorizeUrl(provider: OauthProviderConfig, nonce: string) {
  const authorizeUrl = provider.authorizeUrl.trim()
  if (!authorizeUrl) return ''

  let url: URL

  try {
    url = new URL(authorizeUrl)
  } catch (error) {
    return ''
  }

  url.searchParams.set('client_id', provider.clientId.trim())
  url.searchParams.set('redirect_uri', provider.redirectUri.trim())
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('state', nonce)

  if (provider.scope.trim()) {
    url.searchParams.set('scope', provider.scope.trim())
  }

  return url.toString()
}

function cleanupExpiredOauthState() {
  const now = Date.now()

  for (let i = sessionStorage.length - 1; i >= 0; i -= 1) {
    const key = sessionStorage.key(i)
    if (!key || !key.startsWith(OAUTH_LOGIN_STATE_PREFIX)) continue

    try {
      const raw = sessionStorage.getItem(key)
      if (!raw) continue

      const value = JSON.parse(raw) as Partial<OauthLoginState>
      if (
        typeof value.createdAt !== 'number' ||
        now - value.createdAt > OAUTH_LOGIN_STATE_TTL
      ) {
        sessionStorage.removeItem(key)
      }
    } catch (error) {
      sessionStorage.removeItem(key)
    }
  }
}

export function beginThirdPartyLogin(settings: SystemSettings, redirectRaw: unknown) {
  const provider = getDefaultEnabledProvider(settings)
  if (!provider) {
    return { ok: false as const, message: '未找到可用的默认认证平台' }
  }

  const redirectPath = sanitizeRedirectPath(redirectRaw) || '/admin/dashboard'
  const nonce = createNonce()
  const stateKey = `${OAUTH_LOGIN_STATE_PREFIX}${nonce}`

  cleanupExpiredOauthState()

  sessionStorage.setItem(
    stateKey,
    JSON.stringify({
      nonce,
      providerId: provider.id,
      redirectPath,
      createdAt: Date.now()
    } satisfies OauthLoginState)
  )

  const url = buildAuthorizeUrl(provider, nonce)

  if (!url) {
    sessionStorage.removeItem(stateKey)
    return { ok: false as const, message: '认证平台授权地址无效' }
  }

  return {
    ok: true as const,
    url,
    provider
  }
}

export function consumeOauthLoginState(nonceRaw: unknown) {
  const nonce = String(nonceRaw || '').trim()
  if (!nonce) return null

  const key = `${OAUTH_LOGIN_STATE_PREFIX}${nonce}`
  const raw = sessionStorage.getItem(key)
  sessionStorage.removeItem(key)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as OauthLoginState

    if (
      !parsed ||
      typeof parsed.createdAt !== 'number' ||
      typeof parsed.providerId !== 'string' ||
      typeof parsed.redirectPath !== 'string' ||
      Date.now() - parsed.createdAt > OAUTH_LOGIN_STATE_TTL
    ) {
      return null
    }

    return parsed
  } catch (error) {
    return null
  }
}
