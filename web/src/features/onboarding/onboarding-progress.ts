export type OnboardingGoal = 'USE' | 'PUBLISH'
export type OnboardingTask = 'discover' | 'agents' | 'skills' | 'tools' | 'detail' | 'use' | 'publish'
export type OnboardingJourneyStep =
  | 'start'
  | 'find'
  | 'agents'
  | 'tools'
  | 'open'
  | 'inspectHeader'
  | 'inspectStatus'
  | 'inspectTabs'
  | 'practice'
  | 'use'
  | 'useComplete'
  | 'publishEntry'
  | 'publishBasics'
  | 'publishCategory'
  | 'publishDocumentation'
  | 'publishScope'
  | 'publishSubmit'
  | 'manage'

const CHANGE_EVENT = 'joyhub:onboarding-progress-change'
const OPEN_GUIDE_EVENT = 'joyhub:open-onboarding-guide'
const memory = new Map<string, string>()

function key(userId: string, value: string) {
  return `joyhub-onboarding-v5:${userId}:${value}`
}

function read(userId: string, value: string) {
  const storageKey = key(userId, value)
  try {
    return window.localStorage?.getItem(storageKey) ?? memory.get(storageKey) ?? null
  } catch {
    return memory.get(storageKey) ?? null
  }
}

function write(userId: string, value: string, next: string) {
  const storageKey = key(userId, value)
  memory.set(storageKey, next)
  try {
    window.localStorage?.setItem(storageKey, next)
  } catch {
    // Keep the current session functional if storage is unavailable.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function hasChosenOnboardingGoal(userId: string) {
  return read(userId, 'goal') === 'USE' || read(userId, 'goal') === 'PUBLISH'
}

export function hasSeenOnboardingWelcome(userId: string) {
  return read(userId, 'welcome') === 'seen' || hasChosenOnboardingGoal(userId) || read(userId, 'journey') !== null
}

export function markOnboardingWelcomeSeen(userId: string) {
  write(userId, 'welcome', 'seen')
}

export function getOnboardingGoal(userId: string): OnboardingGoal | null {
  const goal = read(userId, 'goal')
  return goal === 'USE' || goal === 'PUBLISH' ? goal : null
}

export function chooseOnboardingGoal(userId: string, goal: OnboardingGoal) {
  write(userId, 'goal', goal)
}

export function getOnboardingJourneyStep(userId: string): OnboardingJourneyStep | null {
  const value = read(userId, 'journey')
  return ['start', 'find', 'agents', 'tools', 'open', 'inspectHeader', 'inspectStatus', 'inspectTabs', 'practice', 'use', 'useComplete', 'publishEntry', 'publishBasics', 'publishCategory', 'publishDocumentation', 'publishScope', 'publishSubmit', 'manage'].includes(value ?? '')
    ? value as OnboardingJourneyStep
    : null
}

export function hasActiveOnboardingJourney(userId: string | undefined) {
  return userId !== undefined && getOnboardingJourneyStep(userId) !== null
}

export function startOnboardingJourney(userId: string, initialStep: OnboardingJourneyStep = 'start') {
  // A prior resource use must not satisfy the first-use step of a new journey.
  write(userId, 'journey:use', 'pending')
  write(userId, 'journey:resume', initialStep)
  write(userId, 'journey', initialStep)
}

export function advanceOnboardingJourney(userId: string, step: OnboardingJourneyStep) {
  write(userId, 'journey:resume', step)
  write(userId, 'journey', step)
}

export function finishOnboardingJourney(userId: string) {
  write(userId, 'journey', 'done')
}

/** Hides the panel without losing the exact cross-page step. */
export function pauseOnboardingJourney(userId: string) {
  const current = getOnboardingJourneyStep(userId)
  if (current) write(userId, 'journey:resume', current)
  write(userId, 'journey', 'paused')
}

export function getPausedOnboardingJourneyStep(userId: string): OnboardingJourneyStep | null {
  const value = read(userId, 'journey:resume')
  return ['start', 'find', 'agents', 'tools', 'open', 'inspectHeader', 'inspectStatus', 'inspectTabs', 'practice', 'use', 'useComplete', 'publishEntry', 'publishBasics', 'publishCategory', 'publishDocumentation', 'publishScope', 'publishSubmit', 'manage'].includes(value ?? '')
    ? value as OnboardingJourneyStep
    : null
}

export function resumeOnboardingJourney(userId: string) {
  const step = getOnboardingJourneyStep(userId) ?? getPausedOnboardingJourneyStep(userId) ?? 'start'
  startOnboardingJourney(userId, step)
  return step
}

export function saveOnboardingJourneyPath(userId: string, pathname: string) {
  write(userId, 'journey:path', pathname)
}

export function getOnboardingJourneyPath(userId: string) {
  const pathname = read(userId, 'journey:path')
  return pathname?.startsWith('/') ? pathname : null
}

export function completeOnboardingTask(userId: string | undefined, task: OnboardingTask) {
  if (userId) write(userId, `task:${task}`, 'done')
}

/**
 * Records a real use action from either the preparation or execution step.
 * People often click the real entry while reading the preparation guidance, so that direct
 * action should continue the journey instead of leaving it on a stale step.
 */
export function completeOnboardingJourneyUse(userId: string | undefined) {
  if (!userId) return
  write(userId, 'task:use', 'done')
  const step = getOnboardingJourneyStep(userId)
  if (step === 'practice' || step === 'use') {
    write(userId, 'journey:use', 'done')
    advanceOnboardingJourney(userId, 'useComplete')
  }
}

export function hasCompletedOnboardingJourneyUse(userId: string) {
  return read(userId, 'journey:use') === 'done'
}

export function getOnboardingTasks(userId: string): Record<OnboardingTask, boolean> {
  return {
    discover: read(userId, 'task:discover') === 'done',
    agents: read(userId, 'task:agents') === 'done',
    skills: read(userId, 'task:skills') === 'done',
    tools: read(userId, 'task:tools') === 'done',
    detail: read(userId, 'task:detail') === 'done',
    use: read(userId, 'task:use') === 'done',
    publish: read(userId, 'task:publish') === 'done',
  }
}

export function subscribeOnboardingProgress(listener: () => void) {
  window.addEventListener(CHANGE_EVENT, listener)
  return () => window.removeEventListener(CHANGE_EVENT, listener)
}

export function openOnboardingGuide() {
  window.dispatchEvent(new Event(OPEN_GUIDE_EVENT))
}

export function subscribeOnboardingGuideOpen(listener: () => void) {
  window.addEventListener(OPEN_GUIDE_EVENT, listener)
  return () => window.removeEventListener(OPEN_GUIDE_EVENT, listener)
}
