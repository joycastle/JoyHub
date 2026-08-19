// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import {
  completeOnboardingJourneyUse,
  completeOnboardingTask,
  hasCompletedOnboardingJourneyUse,
  hasSeenOnboardingWelcome,
  markOnboardingWelcomeSeen,
  getOnboardingJourneyStep,
  startOnboardingJourney,
} from './onboarding-progress'

describe('onboarding journey use completion', () => {
  it('requires an actual use action in the current journey', () => {
    const userId = `new-user-${Date.now()}`
    startOnboardingJourney(userId, 'use')

    // A stale global use task must not skip the current resource action.
    completeOnboardingTask(userId, 'use')
    expect(hasCompletedOnboardingJourneyUse(userId)).toBe(false)

    completeOnboardingJourneyUse(userId)
    expect(hasCompletedOnboardingJourneyUse(userId)).toBe(true)

    // Starting over intentionally requires another use action.
    startOnboardingJourney(userId, 'use')
    expect(hasCompletedOnboardingJourneyUse(userId)).toBe(false)
  })

  it('continues when the user opens the real entry from the preparation step', () => {
    const userId = `direct-use-${Date.now()}`
    startOnboardingJourney(userId, 'practice')

    completeOnboardingJourneyUse(userId)

    expect(hasCompletedOnboardingJourneyUse(userId)).toBe(true)
    expect(getOnboardingJourneyStep(userId)).toBe('useComplete')
  })

  it('treats an existing journey or explicit dismiss as a seen welcome', () => {
    const firstVisit = `first-visit-${Date.now()}`
    expect(hasSeenOnboardingWelcome(firstVisit)).toBe(false)

    markOnboardingWelcomeSeen(firstVisit)
    expect(hasSeenOnboardingWelcome(firstVisit)).toBe(true)

    const returning = `returning-${Date.now()}`
    startOnboardingJourney(returning, 'start')
    expect(hasSeenOnboardingWelcome(returning)).toBe(true)
  })
})
