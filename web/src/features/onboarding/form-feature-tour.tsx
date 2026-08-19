import { useLayoutEffect, useRef, useState } from 'react'
import { CheckCircle2, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { getOnboardingPanelPosition, getOnboardingViewport } from './onboarding-panel-position'

export interface FormTourStep { target: string; title: string; description: string }

/** Highlights one real form control at a time without covering or disabling the form. */
export function FormFeatureTour({ steps, onDismiss, label = '实地导览', completeLabel = '我可以发布了' }: { steps: FormTourStep[]; onDismiss: () => void; label?: string; completeLabel?: string }) {
  const [index, setIndex] = useState(0)
  const [visible, setVisible] = useState(true)
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const step = steps[index]

  useLayoutEffect(() => {
    setPosition(null)
    const target = document.querySelector<HTMLElement>(`[data-onboarding-target="${step.target}"]`)
    target?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
    target?.classList.add('relative', 'z-30', 'rounded-lg', 'ring-4', 'ring-primary/50', 'ring-offset-4')
    const updatePosition = () => {
      const panel = panelRef.current
      if (!target || !panel || window.innerWidth < 768) {
        setPosition((current) => current === null ? current : null)
        return
      }
      const next = getOnboardingPanelPosition(target.getBoundingClientRect(), panel.getBoundingClientRect(), getOnboardingViewport())
      setPosition((current) => current && current.left === next.left && current.top === next.top ? current : next)
    }
    const frame = window.requestAnimationFrame(() => window.requestAnimationFrame(updatePosition))
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updatePosition)
    if (panelRef.current) resizeObserver?.observe(panelRef.current)
    if (target) resizeObserver?.observe(target)
    window.visualViewport?.addEventListener('resize', updatePosition)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      resizeObserver?.disconnect()
      window.visualViewport?.removeEventListener('resize', updatePosition)
      target?.classList.remove('relative', 'z-30', 'rounded-lg', 'ring-4', 'ring-primary/50', 'ring-offset-4')
    }
  }, [step.target])

  const dismiss = () => { setVisible(false); onDismiss() }
  if (!visible) return null
  return <aside ref={panelRef} className={position ? 'fixed z-[60] w-[min(26rem,calc(100vw-2rem))] rounded-xl border bg-white p-5 shadow-xl' : 'fixed inset-x-4 bottom-4 z-[60] mx-auto w-[min(26rem,calc(100vw-2rem))] rounded-xl border bg-white p-5 shadow-xl'} style={position ?? undefined} role="region" aria-label={label}>
    <div className="flex items-start gap-3"><span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">{index + 1}</span><div className="min-w-0 flex-1"><p className="text-xs font-semibold uppercase tracking-wide text-primary">{label} {index + 1} / {steps.length}</p><h2 className="mt-1 text-base font-semibold">{step.title}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{step.description}</p></div><button type="button" onClick={dismiss} aria-label={`退出${label}`} className="text-muted-foreground hover:text-foreground"><X className="h-4 w-4" /></button></div>
    <div className="mt-4 flex justify-between gap-3"><Button variant="outline" size="sm" disabled={index === 0} onClick={() => setIndex((value) => value - 1)}><ChevronLeft className="mr-1 h-4 w-4" />上一步</Button><Button size="sm" onClick={() => index === steps.length - 1 ? dismiss() : setIndex((value) => value + 1)}>{index === steps.length - 1 ? <><CheckCircle2 className="mr-1 h-4 w-4" />{completeLabel}</> : <>下一步<ChevronRight className="ml-1 h-4 w-4" /></>}</Button></div>
  </aside>
}
