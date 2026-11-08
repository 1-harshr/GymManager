/**
 * Settings kept in this browser, as the phone keeps them on the phone: theme, Home switches, grid view,
 * when each member was last reminded, and the alerts inbox. Storage can be unavailable (private
 * windows), so every read and write falls back quietly.
 */
import { useSyncExternalStore } from 'react'

const listeners = new Set<() => void>()

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

const cache = new Map<string, unknown>()

export function getPref<T>(key: string, fallback: T): T {
  if (!cache.has(key)) cache.set(key, read(key, fallback))
  return cache.get(key) as T
}

export function setPref<T>(key: string, value: T) {
  cache.set(key, value)
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Kept for this visit only.
  }
  listeners.forEach((l) => l())
}

export function usePref<T>(key: string, fallback: T): [T, (value: T) => void] {
  const value = useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => getPref(key, fallback),
  )
  return [value, (next: T) => setPref(key, next)]
}

export type Theme = 'system' | 'light' | 'dark'

export function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'system') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', theme)
}

/** When each member was last sent a reminder from this browser, per gym. */
export function reminderLogKey(gymId: string) {
  return `reminders-sent:${gymId}`
}

/** When each member was last sent a "we miss you" from this browser, per gym. */
export function missYouLogKey(gymId: string) {
  return `miss-you-sent:${gymId}`
}
