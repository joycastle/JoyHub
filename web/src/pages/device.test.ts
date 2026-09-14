// @vitest-environment jsdom

import { act, fireEvent, render, screen } from '@testing-library/react'
import { createElement, forwardRef } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const { fetchJsonMock, navigateMock } = vi.hoisted(() => ({
  fetchJsonMock: vi.fn(),
  navigateMock: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: React.ReactNode }) => createElement('div', null, children),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
    createElement('button', props, children),
}))

vi.mock('@/shared/ui/input', () => ({
  Input: forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
    (props, ref) => createElement('input', { ...props, ref }),
  ),
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children }: { children: React.ReactNode }) => createElement('label', null, children),
}))

vi.mock('@/api/client', () => ({
  fetchJson: fetchJsonMock,
  getCsrfHeaders: () => ({}),
}))

vi.mock('@/shared/lib/error-display', () => ({
  truncateErrorMessage: (m: string) => m,
}))

import { DeviceAuthPage } from './device'

describe('DeviceAuthPage', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('exports a named component function', () => {
    expect(typeof DeviceAuthPage).toBe('function')
    expect(DeviceAuthPage.name).toBe('DeviceAuthPage')
  })

  it('returns to the home page shortly after successful authorization', async () => {
    vi.useFakeTimers()
    fetchJsonMock.mockResolvedValue(undefined)
    window.history.replaceState({}, '', '/cli/auth?user_code=ABCD-2345')
    render(createElement(DeviceAuthPage))

    fireEvent.click(screen.getByRole('button', { name: 'device.submit' }))
    await act(async () => Promise.resolve())

    expect(fetchJsonMock).toHaveBeenCalledWith('/api/v1/device/authorize', expect.any(Object))
    expect(screen.getByText('device.success')).toBeDefined()

    act(() => {
      vi.advanceTimersByTime(1500)
    })
    expect(navigateMock).toHaveBeenCalledWith({ to: '/', replace: true })
  })
})
