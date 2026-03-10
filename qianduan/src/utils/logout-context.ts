export type LogoutEntry = 'admin' | 'user'

const LOGOUT_ENTRY_KEY = 'LOGOUT_ENTRY'
const LOGOUT_ROLE_KEY = 'LOGOUT_ROLE'

export function setLogoutContext(entry: LogoutEntry, role: string) {
  try {
    sessionStorage.setItem(LOGOUT_ENTRY_KEY, entry)
    sessionStorage.setItem(LOGOUT_ROLE_KEY, String(role || ''))
  } catch (error) {
    // keep logout flow resilient when storage is unavailable
  }
}

export function readLogoutContext() {
  try {
    return {
      entry: (sessionStorage.getItem(LOGOUT_ENTRY_KEY) || '').trim().toLowerCase(),
      role: (sessionStorage.getItem(LOGOUT_ROLE_KEY) || '').trim()
    }
  } catch (error) {
    return {
      entry: '',
      role: ''
    }
  }
}

export function clearLogoutContext() {
  try {
    sessionStorage.removeItem(LOGOUT_ENTRY_KEY)
    sessionStorage.removeItem(LOGOUT_ROLE_KEY)
  } catch (error) {
    // ignore storage cleanup failure
  }
}
