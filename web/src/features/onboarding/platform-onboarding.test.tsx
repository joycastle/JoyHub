// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) =>
      values ? `${key}:${Object.values(values).join('/')}` : key,
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

import { PlatformOnboarding } from './platform-onboarding'

describe('PlatformOnboarding', () => {
  afterEach(() => {
    cleanup()
    navigateMock.mockClear()
  })

  it('opens after sign-in and advances through the walkthrough', () => {
    render(<PlatformOnboarding userId="user-a" displayName="Mia" />)

    expect(screen.getByText('onboarding.loginGreeting:Mia')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.next' }))
    expect(screen.getByText('onboarding.steps.agents.title')).toBeTruthy()
  })

  it('only opens automatically once per user and remains replayable', () => {
    const { rerender } = render(<PlatformOnboarding userId="user-b" />)

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.skip' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    rerender(<PlatformOnboarding />)
    rerender(<PlatformOnboarding userId="user-b" />)
    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.replay' }))
    expect(screen.getByText('onboarding.loginGreeting:onboarding.member')).toBeTruthy()
  })

  it('opens a center with its contextual introduction', () => {
    render(<PlatformOnboarding userId="user-c" />)

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.welcome.action' }))
    expect(navigateMock).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.agents.action' }))

    expect(navigateMock).toHaveBeenCalledWith({ to: '/agents', search: { onboarding: true } })
  })

  it('keeps an entry point back to the guide after opening content or search', () => {
    render(<PlatformOnboarding userId="user-d" />)

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.content.shortTitle' }))
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.content.action' }))
    expect(navigateMock).toHaveBeenCalledWith({ to: '/dashboard/resources', search: { onboarding: true } })

    fireEvent.click(screen.getByRole('button', { name: 'onboarding.replay' }))
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.search.shortTitle' }))
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.steps.search.action' }))
    expect(navigateMock).toHaveBeenCalledWith({
      to: '/search',
      search: { q: '', sort: 'newest', page: 0, starredOnly: false, onboarding: true },
    })
  })
})
