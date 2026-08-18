// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) => values ? `${key}:${Object.values(values).join('/')}` : key,
  }),
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => navigateMock }))

import { PlatformOnboarding } from './platform-onboarding'
import { chooseOnboardingGoal, getOnboardingGoal, getOnboardingJourneyStep, startOnboardingJourney } from './onboarding-progress'

describe('PlatformOnboarding', () => {
  afterEach(() => {
    cleanup()
    navigateMock.mockClear()
  })

  it('waits for an explicit click before offering onboarding goals', () => {
    render(<PlatformOnboarding userId="user-a" displayName="Mia" />)

    expect(screen.queryByText('onboarding.welcome.title:Mia')).toBeNull()
    expect(getOnboardingJourneyStep('user-a')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.replay' }))
    expect(screen.getByText('onboarding.welcome.title:Mia')).toBeTruthy()
    fireEvent.click(screen.getByText('onboarding.welcome.useAction'))

    expect(getOnboardingJourneyStep('user-a')).toBe('start')
    expect(navigateMock).toHaveBeenCalledWith({ to: '/' })
  })

  it('keeps free exploration outside the onboarding journey', () => {
    render(<PlatformOnboarding userId="user-b" />)

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.replay' }))
    fireEvent.click(screen.getByText('onboarding.welcome.explore'))

    expect(screen.queryByText('onboarding.welcome.title:onboarding.member')).toBeNull()
    expect(getOnboardingGoal('user-b')).toBeNull()
    expect(getOnboardingJourneyStep('user-b')).toBeNull()
  })

  it('only resumes an existing publishing journey after an explicit click', () => {
    chooseOnboardingGoal('user-c', 'PUBLISH')
    startOnboardingJourney('user-c', 'publishEntry')
    render(<PlatformOnboarding userId="user-c" />)

    expect(navigateMock).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.replay' }))
    expect(navigateMock).toHaveBeenCalledWith({ to: '/dashboard/resources' })
  })
})
