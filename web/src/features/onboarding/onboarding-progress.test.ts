// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import {
  completeOnboardingJourneyUse,
  completeOnboardingTask,
  hasCompletedOnboardingJourneyUse,
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
})
