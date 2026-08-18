import { describe, expect, it } from 'vitest'
import { getOnboardingPanelPosition } from './onboarding-panel-position'

const panel = { width: 416, height: 280 }
const viewport = { width: 1440, height: 900 }

describe('getOnboardingPanelPosition', () => {
  it('moves a panel to the left when the target is near the right edge', () => {
    const position = getOnboardingPanelPosition(
      { left: 1260, right: 1400, top: 260, bottom: 340, width: 140, height: 80 },
      panel,
      viewport,
    )

    expect(position.left).toBeLessThan(1260)
    expect(position.left).toBeGreaterThanOrEqual(16)
    expect(position.left + panel.width).toBeLessThanOrEqual(viewport.width - 16)
  })

  it('keeps wide-target fallbacks completely inside the viewport', () => {
    const position = getOnboardingPanelPosition(
      { left: 40, right: 1400, top: 520, bottom: 620, width: 1360, height: 100 },
      panel,
      viewport,
    )

    expect(position.left).toBeGreaterThanOrEqual(16)
    expect(position.top).toBeGreaterThanOrEqual(16)
    expect(position.left + panel.width).toBeLessThanOrEqual(viewport.width - 16)
    expect(position.top + panel.height).toBeLessThanOrEqual(viewport.height - 16)
  })

  it('clamps oversized panels to a safe top-left boundary', () => {
    const position = getOnboardingPanelPosition(
      { left: 10, right: 40, top: 10, bottom: 40, width: 30, height: 30 },
      { width: 760, height: 620 },
      { width: 700, height: 560 },
    )

    expect(position).toEqual({ left: 16, top: 16 })
  })
})
