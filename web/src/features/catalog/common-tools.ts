import { useEffect, useState } from 'react'

const STORAGE_KEY = 'joyhub.common-tools.v1'
const AUTO_ADD_THRESHOLD = 3
const AUTO_ADD_WINDOW_MS = 30 * 24 * 60 * 60 * 1000

interface CommonToolsState {
  manualToolIds: number[]
  automaticToolIds: number[]
  automaticSuppressedToolIds: number[]
  useCounts: Record<string, number>
  windowStartedAt: Record<string, number>
}

const EMPTY_STATE: CommonToolsState = {
  manualToolIds: [],
  automaticToolIds: [],
  automaticSuppressedToolIds: [],
  useCounts: {},
  windowStartedAt: {},
}

function readState(): CommonToolsState {
  if (typeof window === 'undefined') return EMPTY_STATE
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '')
    if (value && typeof value === 'object') {
      const state = value as Partial<CommonToolsState>
      return {
        manualToolIds: Array.isArray(state.manualToolIds) ? state.manualToolIds.filter(Number.isInteger) : [],
        automaticToolIds: Array.isArray(state.automaticToolIds) ? state.automaticToolIds.filter(Number.isInteger) : [],
        automaticSuppressedToolIds: Array.isArray(state.automaticSuppressedToolIds) ? state.automaticSuppressedToolIds.filter(Number.isInteger) : [],
        useCounts: state.useCounts && typeof state.useCounts === 'object' ? state.useCounts : {},
        windowStartedAt: state.windowStartedAt && typeof state.windowStartedAt === 'object' ? state.windowStartedAt : {},
      }
    }
  } catch {
    // Ignore an invalid older browser value and start with a clean personal list.
  }
  return EMPTY_STATE
}

function writeState(state: CommonToolsState) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  window.dispatchEvent(new Event('joyhub:common-tools-changed'))
}

function orderedToolIds(state: CommonToolsState) {
  return [...state.manualToolIds, ...state.automaticToolIds.filter((id) => !state.manualToolIds.includes(id))]
}

/** Browser-local personal tool shortcuts and frequency tracking. */
export function useCommonTools() {
  const [state, setState] = useState<CommonToolsState>(readState)

  useEffect(() => {
    const sync = () => setState(readState())
    window.addEventListener('joyhub:common-tools-changed', sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener('joyhub:common-tools-changed', sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  const update = (next: CommonToolsState) => {
    setState(next)
    writeState(next)
  }

  const toggleTool = (toolId: number) => {
    const isManual = state.manualToolIds.includes(toolId)
    update({
      ...state,
      manualToolIds: isManual
        ? state.manualToolIds.filter((id) => id !== toolId)
        : [toolId, ...state.manualToolIds],
      automaticToolIds: isManual ? state.automaticToolIds.filter((id) => id !== toolId) : state.automaticToolIds,
      automaticSuppressedToolIds: isManual
        ? [...new Set([...state.automaticSuppressedToolIds, toolId])]
        : state.automaticSuppressedToolIds.filter((id) => id !== toolId),
    })
  }

  const recordToolUse = (toolId: number) => {
    const key = String(toolId)
    const now = Date.now()
    const isOutsideWindow = now - (state.windowStartedAt[key] ?? now) > AUTO_ADD_WINDOW_MS
    const nextCount = isOutsideWindow ? 1 : (state.useCounts[key] ?? 0) + 1
    const isAlreadyCommon = orderedToolIds(state).includes(toolId)
    const shouldAutoAdd = !isAlreadyCommon && !state.automaticSuppressedToolIds.includes(toolId) && nextCount >= AUTO_ADD_THRESHOLD
    update({
      ...state,
      automaticToolIds: shouldAutoAdd
        ? [toolId, ...state.automaticToolIds.filter((id) => id !== toolId)]
        : state.automaticToolIds,
      useCounts: { ...state.useCounts, [key]: nextCount },
      windowStartedAt: { ...state.windowStartedAt, [key]: isOutsideWindow ? now : (state.windowStartedAt[key] ?? now) },
    })
  }

  const toolIds = orderedToolIds(state)
  return {
    toolIds,
    isCommonTool: (toolId: number) => toolIds.includes(toolId),
    toggleTool,
    recordToolUse,
  }
}
