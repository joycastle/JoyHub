import { useLayoutEffect, useMemo, useRef, useState, type ComponentType } from 'react'
import { createPortal } from 'react-dom'
import { BookOpenCheck, LayoutGrid, Plus, Search, SlidersHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { CatalogCenter } from '@/api/types'
import { Button } from '@/shared/ui/button'

export type CenterTourTarget = 'search' | 'quickBrowse' | 'filters' | 'catalog' | 'publish'
type GuidedCenter = CatalogCenter | 'SKILL' | 'LANDING' | 'CONTENT'

interface TourStep {
  key: string
  target: CenterTourTarget
  icon: ComponentType<{ className?: string }>
}

const CENTER_STEPS: Record<GuidedCenter, TourStep[]> = {
  AGENT: [
    { key: 'search', target: 'search', icon: Search },
    { key: 'quickBrowse', target: 'quickBrowse', icon: LayoutGrid },
    { key: 'filters', target: 'filters', icon: SlidersHorizontal },
    { key: 'catalog', target: 'catalog', icon: LayoutGrid },
    { key: 'publish', target: 'publish', icon: Plus },
  ],
  SKILL: [
    { key: 'search', target: 'search', icon: Search },
    { key: 'quickBrowse', target: 'quickBrowse', icon: LayoutGrid },
    { key: 'filters', target: 'filters', icon: SlidersHorizontal },
    { key: 'catalog', target: 'catalog', icon: BookOpenCheck },
    { key: 'publish', target: 'publish', icon: Plus },
  ],
  TOOL: [
    { key: 'search', target: 'search', icon: Search },
    { key: 'quickBrowse', target: 'quickBrowse', icon: LayoutGrid },
    { key: 'filters', target: 'filters', icon: SlidersHorizontal },
    { key: 'catalog', target: 'catalog', icon: LayoutGrid },
    { key: 'publish', target: 'publish', icon: Plus },
  ],
  LANDING: [
    { key: 'search', target: 'search', icon: Search },
    { key: 'quickBrowse', target: 'quickBrowse', icon: LayoutGrid },
    { key: 'filters', target: 'filters', icon: SlidersHorizontal },
    { key: 'catalog', target: 'catalog', icon: BookOpenCheck },
  ],
  CONTENT: [
    { key: 'publish', target: 'publish', icon: Plus },
    { key: 'filters', target: 'filters', icon: SlidersHorizontal },
    { key: 'catalog', target: 'catalog', icon: BookOpenCheck },
  ],
}

interface CenterFeatureTourProps {
  center: GuidedCenter
  hasCatalogItems: boolean
  onDismiss: () => void
  onReturnToOnboarding: () => void
  onTargetChange: (target: CenterTourTarget) => void
}

interface TourPosition {
  left: number
  top: number
}

/**
 * Guides a user through the controls already visible on a resource center page.
 * Each step spotlights one real control and leaves the rest of the page unchanged.
 */
export function CenterFeatureTour({ center, hasCatalogItems, onDismiss, onReturnToOnboarding, onTargetChange }: CenterFeatureTourProps) {
  const { t } = useTranslation()
  const steps = useMemo(
    () => CENTER_STEPS[center].filter((step) => step.target !== 'catalog' || hasCatalogItems),
    [center, hasCatalogItems],
  )
  const [stepIndex, setStepIndex] = useState(0)
  const [position, setPosition] = useState<TourPosition | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const currentStep = steps[stepIndex]
  const StepIcon = currentStep.icon
  const isLastStep = stepIndex === steps.length - 1

  useLayoutEffect(() => {
    const updatePosition = () => {
      const target = document.querySelector<HTMLElement>(`[data-onboarding-target="${currentStep.target}"]`)
      const panel = panelRef.current
      if (!target || !panel || window.innerWidth < 768) {
        setPosition(null)
        return
      }

      const targetRect = target.getBoundingClientRect()
      const panelRect = panel.getBoundingClientRect()
      const margin = 16
      const maxLeft = window.innerWidth - panelRect.width - margin
      const maxTop = window.innerHeight - panelRect.height - margin
      const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)
      const canPlaceRight = targetRect.right + margin + panelRect.width <= window.innerWidth - margin
      const canPlaceLeft = targetRect.left - margin - panelRect.width >= margin

      if (canPlaceRight) {
        setPosition({ left: targetRect.right + margin, top: clamp(targetRect.top, margin, maxTop) })
        return
      }
      if (canPlaceLeft) {
        setPosition({ left: targetRect.left - margin - panelRect.width, top: clamp(targetRect.top, margin, maxTop) })
        return
      }

      const canPlaceBelow = targetRect.bottom + margin + panelRect.height <= window.innerHeight - margin
      setPosition({
        left: clamp(targetRect.left, margin, maxLeft),
        top: canPlaceBelow ? targetRect.bottom + margin : clamp(targetRect.top - margin - panelRect.height, margin, maxTop),
      })
    }

    onTargetChange(currentStep.target)
    document.querySelector<HTMLElement>(`[data-onboarding-target="${currentStep.target}"]`)?.scrollIntoView?.({
      behavior: 'auto',
      block: 'center',
    })
    const firstFrame = window.requestAnimationFrame(() => {
      window.requestAnimationFrame(updatePosition)
    })
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.cancelAnimationFrame(firstFrame)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [currentStep.target, onTargetChange])

  const dismiss = () => {
    onTargetChange('search')
    onDismiss()
  }

  const returnToOnboarding = () => {
    dismiss()
    onReturnToOnboarding()
  }

  return (
    <>
      <div className="fixed inset-0 z-40 bg-slate-950/35 backdrop-blur-[1px]" aria-hidden="true" />
      {createPortal(
        <section
          ref={panelRef}
          className={position
            ? 'fixed z-[2147483647] w-[min(28rem,calc(100vw-2rem))] rounded-lg border bg-background p-5 shadow-xl md:p-6'
            : 'fixed inset-x-4 bottom-4 z-[2147483647] mx-auto max-w-xl rounded-lg border bg-background p-5 shadow-xl md:bottom-8 md:p-6'}
          style={position ?? undefined}
          role="dialog"
          aria-modal="true"
          aria-labelledby="center-feature-tour-title"
        >
          <div className="flex items-start gap-4">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <StepIcon className="h-5 w-5" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-primary">
                {t('centerFeatureTour.counter', { current: stepIndex + 1, total: steps.length })}
              </p>
              <h2 id="center-feature-tour-title" className="mt-1 text-lg font-semibold">
                {t(`centerFeatureTour.centers.${center}.${currentStep.key}.title`)}
              </h2>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                {t(`centerFeatureTour.centers.${center}.${currentStep.key}.description`)}
              </p>
            </div>
          </div>
          <div className="mt-5 flex items-center justify-between gap-3">
            <div className="flex flex-col items-start gap-2 sm:flex-row sm:items-center sm:gap-3">
              <button type="button" className="text-sm font-medium text-muted-foreground hover:text-foreground" onClick={dismiss}>
                {t('centerFeatureTour.skip')}
              </button>
              <button type="button" className="text-sm font-medium text-primary hover:text-primary/80" onClick={returnToOnboarding}>
                {t('centerFeatureTour.returnToOnboarding')}
              </button>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={stepIndex === 0} onClick={() => setStepIndex((index) => index - 1)}>
                {t('centerFeatureTour.previous')}
              </Button>
              <Button size="sm" onClick={isLastStep ? dismiss : () => setStepIndex((index) => index + 1)}>
                {isLastStep ? t('centerFeatureTour.finish') : t('centerFeatureTour.next')}
              </Button>
            </div>
          </div>
        </section>,
        document.body,
      )}
    </>
  )
}
