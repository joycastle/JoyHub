import { useState } from 'react'
import type { ViewMode } from '@/shared/components/view-mode-toggle'

export function useViewMode(scope: string, initialValue: ViewMode = 'list') {
  const storageKey = `joyhub.view-mode.${scope}`
  const [viewMode, setViewModeState] = useState<ViewMode>(() => {
    if (typeof window === 'undefined') return initialValue
    const saved = window.localStorage.getItem(storageKey)
    return saved === 'grid' || saved === 'list' ? saved : initialValue
  })
  const setViewMode = (value: ViewMode) => {
    setViewModeState(value)
    window.localStorage.setItem(storageKey, value)
  }
  return [viewMode, setViewMode] as const
}
