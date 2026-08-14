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
import { chooseOnboardingGoal, startOnboardingJourney } from './onboarding-progress'

describe('PlatformOnboarding', () => {
  afterEach(() => {
    cleanup()
    navigateMock.mockClear()
  })

  it('asks the first-time user to choose between using and publishing', () => {
    render(<PlatformOnboarding userId="user-a" displayName="Mia" />)

    expect(screen.getByText('onboarding.welcome.title:Mia')).toBeTruthy()
    fireEvent.click(screen.getByText('onboarding.welcome.useAction'))
    expect(navigateMock).toHaveBeenCalledWith({ to: '/' })
  })

  it('does not reopen for the same user after a new login session', () => {
    const { rerender } = render(<PlatformOnboarding userId="user-b" />)
    fireEvent.click(screen.getByText('onboarding.welcome.explore'))
    rerender(<PlatformOnboarding />)
    rerender(<PlatformOnboarding userId="user-b" />)

    expect(screen.queryByText('onboarding.welcome.title:onboarding.member')).toBeNull()
  })

  it('resumes publishing at the real publishing entry', () => {
    chooseOnboardingGoal('user-c', 'PUBLISH')
    startOnboardingJourney('user-c', 'publishEntry')
    render(<PlatformOnboarding userId="user-c" />)
    fireEvent.click(screen.getByRole('button', { name: '重新开始新手引导' }))
    expect(navigateMock).toHaveBeenCalledWith({ to: '/dashboard/resources' })
  })
})
