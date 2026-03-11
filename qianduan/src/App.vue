<script setup lang="ts">
import { watch } from 'vue'
import { useSettingsStore } from './stores/settings'

const settingsStore = useSettingsStore()

function normalizeTitle(value: unknown) {
  const text = String(value || '').trim()
  return text || '问卷调查系统'
}

function upsertIconLink(rel: string, href: string) {
  if (typeof document === 'undefined') return
  let link = document.querySelector(`link[rel='${rel}']`) as HTMLLinkElement | null
  if (!link) {
    link = document.createElement('link')
    link.rel = rel
    document.head.appendChild(link)
  }
  link.href = href
}

function applyFavicon(logoUrl: string) {
  if (typeof document === 'undefined') return
  const fallback = '/favicon.ico'
  const href = logoUrl && logoUrl.trim() ? logoUrl.trim() : fallback
  upsertIconLink('icon', href)
  upsertIconLink('shortcut icon', href)
  upsertIconLink('apple-touch-icon', href)
}

watch(
  () => settingsStore.settings.systemName,
  (value) => {
    if (typeof document === 'undefined') return
    document.title = normalizeTitle(value)
  },
  { immediate: true }
)

watch(
  () => settingsStore.settings.titleLogo,
  (logo) => {
    applyFavicon(String(logo || ''))
  },
  { immediate: true }
)
</script>

<template>
  <router-view />
</template>
