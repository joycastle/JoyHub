export interface OnboardingPanelPosition {
  left: number
  top: number
}

interface RectLike {
  left: number
  right: number
  top: number
  bottom: number
  width: number
  height: number
}

interface ViewportSize {
  width: number
  height: number
}

const DEFAULT_MARGIN = 16

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(Math.max(value, minimum), Math.max(minimum, maximum))
}

/**
 * Picks a location next to the highlighted control, then clamps it to the visible viewport.
 * The final clamp is deliberate: some detail headers are wider than the available space.
 */
export function getOnboardingPanelPosition(
  target: RectLike,
  panel: Pick<RectLike, 'width' | 'height'>,
  viewport: ViewportSize,
  margin = DEFAULT_MARGIN,
): OnboardingPanelPosition {
  const maxLeft = Math.max(margin, viewport.width - panel.width - margin)
  const maxTop = Math.max(margin, viewport.height - panel.height - margin)
  const centeredTop = clamp(target.top + (target.height - panel.height) / 2, margin, maxTop)
  const centeredLeft = clamp(target.left + (target.width - panel.width) / 2, margin, maxLeft)

  const candidates: OnboardingPanelPosition[] = [
    { left: target.right + margin, top: centeredTop },
    { left: target.left - panel.width - margin, top: centeredTop },
    { left: centeredLeft, top: target.bottom + margin },
    { left: centeredLeft, top: target.top - panel.height - margin },
  ]

  const visibleCandidate = candidates.find((candidate) => (
    candidate.left >= margin
    && candidate.top >= margin
    && candidate.left + panel.width <= viewport.width - margin
    && candidate.top + panel.height <= viewport.height - margin
  ))

  const selected = visibleCandidate ?? candidates[2]
  return {
    left: clamp(selected.left, margin, maxLeft),
    top: clamp(selected.top, margin, maxTop),
  }
}

export function getOnboardingViewport(): ViewportSize {
  return {
    width: document.documentElement.clientWidth || window.innerWidth,
    height: document.documentElement.clientHeight || window.innerHeight,
  }
}
