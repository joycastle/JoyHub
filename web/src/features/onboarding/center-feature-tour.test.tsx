// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, number>) =>
      values ? `${key}:${values.current}/${values.total}` : key,
  }),
}))

import { CenterFeatureTour } from './center-feature-tour'

describe('CenterFeatureTour', () => {
  afterEach(() => {
    cleanup()
  })

  it('walks through the Agent center controls one at a time', () => {
    const onTargetChange = vi.fn()
    const onDismiss = vi.fn()
    const onReturnToOnboarding = vi.fn()

    render(
      <CenterFeatureTour
        center="AGENT"
        hasCatalogItems
        onTargetChange={onTargetChange}
        onDismiss={onDismiss}
        onReturnToOnboarding={onReturnToOnboarding}
      />,
    )

    expect(onTargetChange).toHaveBeenLastCalledWith('search')
    expect(screen.getByText('centerFeatureTour.centers.AGENT.search.title')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'centerFeatureTour.next' }))
    expect(onTargetChange).toHaveBeenLastCalledWith('catalog')
    expect(screen.getByText('centerFeatureTour.centers.AGENT.catalog.title')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'centerFeatureTour.returnToOnboarding' }))
    expect(onDismiss).toHaveBeenCalledOnce()
    expect(onReturnToOnboarding).toHaveBeenCalledOnce()
  })
})
