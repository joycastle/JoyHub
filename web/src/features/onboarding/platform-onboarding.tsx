import { useEffect, useRef, useState, type ComponentType } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Bot, Boxes, CheckCircle2, Compass, FolderCog, Search, Sparkles, Wrench } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { RESUME_PLATFORM_ONBOARDING_EVENT } from './onboarding-events'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'

interface OnboardingStep {
  key: 'welcome' | 'agents' | 'skills' | 'tools' | 'content' | 'search'
  icon: ComponentType<{ className?: string }>
}

const ONBOARDING_STEPS: OnboardingStep[] = [
  { key: 'welcome', icon: Sparkles },
  { key: 'agents', icon: Bot },
  { key: 'skills', icon: Boxes },
  { key: 'tools', icon: Wrench },
  { key: 'content', icon: FolderCog },
  { key: 'search', icon: Search },
]
// Bump this key when the guided product map changes materially, so every existing employee sees it once.
const ONBOARDING_SEEN_STORAGE_PREFIX = 'joyhub-platform-onboarding-v2-seen:'
const inMemorySeenOnboarding = new Set<string>()

function hasSeenOnboarding(storageKey: string) {
  try {
    return window.localStorage?.getItem(storageKey) === 'true' || inMemorySeenOnboarding.has(storageKey)
  } catch {
    return inMemorySeenOnboarding.has(storageKey)
  }
}

function markOnboardingSeen(storageKey: string) {
  inMemorySeenOnboarding.add(storageKey)
  try {
    window.localStorage?.setItem(storageKey, 'true')
  } catch {
    // The in-memory marker keeps the current session stable if storage is unavailable.
  }
}

interface PlatformOnboardingProps {
  userId?: string
  displayName?: string
}

/** A task-focused introduction shown at the start of every authenticated session. */
export function PlatformOnboarding({ userId, displayName }: PlatformOnboardingProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const previousUserIdRef = useRef<string | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [stepIndex, setStepIndex] = useState(0)
  const currentStep = ONBOARDING_STEPS[stepIndex]
  const isFirstStep = stepIndex === 0
  const isLastStep = stepIndex === ONBOARDING_STEPS.length - 1
  const StepIcon = currentStep.icon

  useEffect(() => {
    if (!userId) {
      previousUserIdRef.current = null
      setIsOpen(false)
      return
    }

    if (previousUserIdRef.current !== userId) {
      previousUserIdRef.current = userId
      const storageKey = `${ONBOARDING_SEEN_STORAGE_PREFIX}${userId}`
      if (hasSeenOnboarding(storageKey)) {
        setIsOpen(false)
        return
      }

      markOnboardingSeen(storageKey)
      setStepIndex(0)
      setIsOpen(true)
    }
  }, [userId])

  useEffect(() => {
    const resume = () => setIsOpen(true)
    window.addEventListener(RESUME_PLATFORM_ONBOARDING_EVENT, resume)
    return () => window.removeEventListener(RESUME_PLATFORM_ONBOARDING_EVENT, resume)
  }, [])

  if (!userId) {
    return null
  }

  const dismiss = () => {
    setIsOpen(false)
  }

  const replay = () => {
    setStepIndex(0)
    setIsOpen(true)
  }

  const openCurrentCenter = () => {
    switch (currentStep.key) {
      case 'welcome':
        setStepIndex(1)
        return
      case 'agents':
        dismiss()
        navigate({ to: '/agents', search: { onboarding: true } })
        return
      case 'skills':
        dismiss()
        navigate({ to: '/skills', search: { onboarding: true } })
        return
      case 'tools':
        dismiss()
        navigate({ to: '/tools', search: { onboarding: true } })
        return
      case 'content':
        dismiss()
        navigate({ to: '/dashboard/resources', search: { onboarding: true } })
        return
      case 'search':
        dismiss()
        navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false, onboarding: true } })
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={replay}
        className="inline-flex h-9 w-9 items-center justify-center rounded-full transition-colors hover:bg-secondary hover:text-foreground"
        aria-label={t('onboarding.replay')}
        title={t('onboarding.replay')}
      >
        <Compass className="h-5 w-5" strokeWidth={1.8} />
      </button>

      <Dialog open={isOpen} onOpenChange={setIsOpen}>
        <DialogContent className="w-[min(calc(100vw-2rem),50rem)] gap-0 overflow-hidden p-0 md:grid md:grid-cols-[15rem_1fr]">
          <aside className="bg-slate-950 px-5 py-6 text-slate-100 md:px-6 md:py-8">
            <div className="flex items-center gap-2 text-sm font-semibold text-sky-300">
              <Sparkles className="h-4 w-4" />
              {t('onboarding.planLabel')}
            </div>
            <h2 className="mt-3 text-xl font-semibold tracking-tight">{t('onboarding.planTitle')}</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">{t('onboarding.planDescription')}</p>

            <div className="mt-7 space-y-1">
              {ONBOARDING_STEPS.map((step, index) => {
                const Icon = step.icon
                const selected = index === stepIndex
                return (
                  <button
                    key={step.key}
                    type="button"
                    onClick={() => setStepIndex(index)}
                    aria-current={selected ? 'step' : undefined}
                    className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm transition-colors ${
                      selected ? 'bg-white/15 text-white shadow-sm' : 'text-slate-300 hover:bg-white/10 hover:text-white'
                    }`}
                  >
                    <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${selected ? 'bg-sky-400 text-slate-950' : 'bg-white/10 text-slate-300'}`}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className="min-w-0 flex-1 truncate">{t(`onboarding.steps.${step.key}.shortTitle`)}</span>
                    {index < stepIndex ? <CheckCircle2 className="h-4 w-4 text-sky-300" /> : null}
                  </button>
                )
              })}
            </div>
          </aside>

          <div className="flex min-w-0 flex-col">
            <div className="flex items-center justify-between gap-4 px-7 pb-0 pt-7 md:px-9 md:pt-9">
              <span className="text-sm font-medium text-muted-foreground">
                {t('onboarding.stepCounter', { current: stepIndex + 1, total: ONBOARDING_STEPS.length })}
              </span>
              <button type="button" className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground" onClick={dismiss}>
                {t('onboarding.skip')}
              </button>
            </div>

            <div className="flex-1 px-7 pb-7 pt-8 md:px-9 md:pb-9">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/15">
                <StepIcon className="h-7 w-7" />
              </div>
              <DialogHeader className="mt-6 text-left">
                <DialogTitle className="text-left text-2xl md:text-3xl">
                  {stepIndex === 0 ? t('onboarding.loginGreeting', { name: displayName ?? t('onboarding.member') }) : t(`onboarding.steps.${currentStep.key}.title`)}
                </DialogTitle>
                <DialogDescription className="text-left text-base leading-7">
                  {t(`onboarding.steps.${currentStep.key}.description`)}
                </DialogDescription>
              </DialogHeader>

              {currentStep.key === 'welcome' ? (
                <div className="mt-7 rounded-2xl border bg-secondary/35 p-5">
                  <p className="text-sm font-semibold text-foreground">{t('onboarding.steps.welcome.nextTitle')}</p>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">{t('onboarding.steps.welcome.nextDescription')}</p>
                  <Button variant="outline" className="mt-5" onClick={openCurrentCenter}>
                    {t('onboarding.steps.welcome.action')}
                  </Button>
                </div>
              ) : (
                <div className="mt-7 rounded-2xl border bg-secondary/35 p-5">
                  <p className="text-sm font-semibold text-foreground">{t('onboarding.recommendedAction')}</p>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">{t(`onboarding.steps.${currentStep.key}.howToUse`)}</p>
                  <Button variant="outline" className="mt-5" onClick={openCurrentCenter}>
                    {t(`onboarding.steps.${currentStep.key}.action`)}
                  </Button>
                </div>
              )}
            </div>

            <DialogFooter className="border-t bg-muted/20 px-7 py-5 sm:justify-between md:px-9">
              <div className="flex items-center gap-2" aria-label={t('onboarding.progressLabel')}>
                {ONBOARDING_STEPS.map((step, index) => (
                  <span
                    key={step.key}
                    className={`h-2 rounded-full transition-all ${index === stepIndex ? 'w-6 bg-primary' : 'w-2 bg-muted-foreground/25'}`}
                  />
                ))}
              </div>
              <div className="flex w-full gap-2 sm:w-auto">
                <Button variant="outline" className="flex-1 sm:flex-none" disabled={isFirstStep} onClick={() => setStepIndex((index) => index - 1)}>
                  {t('onboarding.previous')}
                </Button>
                <Button className="flex-1 sm:flex-none" onClick={isLastStep ? dismiss : () => setStepIndex((index) => index + 1)}>
                  {isLastStep ? t('onboarding.finish') : t('onboarding.next')}
                </Button>
              </div>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
