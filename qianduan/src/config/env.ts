export const USE_REAL_API = String(import.meta.env.VITE_USE_REAL_API || 'false') === 'true'

export const API_BASE = import.meta.env.VITE_API_BASE || '/api'