import type { UserRole } from '../stores/auth'

function getSingleQueryValue(raw: unknown) {
  if (Array.isArray(raw)) {
    return String(raw[0] || '')
  }

  return typeof raw === 'string' ? raw : ''
}

export function sanitizeRedirectPath(raw: unknown) {
  const path = getSingleQueryValue(raw).trim()

  if (!path) return ''
  if (!path.startsWith('/')) return ''
  if (path.startsWith('//')) return ''
  if (path.startsWith('/local-admin/login')) return ''

  return path
}

export function buildLoginPathWithRedirect(rawPath: unknown) {
  const redirectPath = sanitizeRedirectPath(rawPath)
  if (!redirectPath) return '/local-admin/login'
  return `/local-admin/login?redirect=${encodeURIComponent(redirectPath)}`
}

export function getPostLoginPath(role: UserRole | '', redirectRaw: unknown) {
  const redirectPath = sanitizeRedirectPath(redirectRaw)

  if (role === 'ROLE1') {
    if (redirectPath && !redirectPath.startsWith('/admin')) {
      return redirectPath
    }

    return '/m'
  }

  if (role === 'ROLE2' || role === 'ROLE3') {
    if (redirectPath) {
      return redirectPath
    }

    return '/admin/dashboard'
  }

  return '/m'
}
