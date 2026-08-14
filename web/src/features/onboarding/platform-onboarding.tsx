import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { ChevronRight, Compass, Search, Send, Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { chooseOnboardingGoal, getOnboardingJourneyPath, hasChosenOnboardingGoal, resumeOnboardingJourney, startOnboardingJourney, type OnboardingGoal } from './onboarding-progress'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

interface PlatformOnboardingProps { userId?: string; displayName?: string }

/** First-run choice starts one cross-page, step-by-step journey. */
export function PlatformOnboarding({ userId, displayName }: PlatformOnboardingProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [isGoalOpen, setIsGoalOpen] = useState(false)
  useEffect(() => {
    if (!userId) return
    // The welcome dialog is a first-login experience. A user can still restart the
    // walkthrough explicitly from the compass button in the header.
    setIsGoalOpen(!hasChosenOnboardingGoal(userId))
  }, [userId])

  if (!userId) return null

  const selectGoal = (goal: OnboardingGoal) => {
    chooseOnboardingGoal(userId, goal)
    startOnboardingJourney(userId, goal === 'PUBLISH' ? 'publishEntry' : 'start')
    setIsGoalOpen(false)
    navigate({ to: goal === 'PUBLISH' ? '/dashboard/resources' : '/' })
  }

  const restartJourney = () => {
    const current = resumeOnboardingJourney(userId)
    startOnboardingJourney(userId, current)
    const lastPath = getOnboardingJourneyPath(userId)
    if (lastPath) {
      const url = new URL(lastPath, window.location.origin)
      const search = Object.fromEntries([...url.searchParams].map(([key, value]) => [key, value === 'true' ? true : value === 'false' ? false : value]))
      navigate({ to: url.pathname as never, search: search as never })
    }
    else navigate({ to: current === 'agents' ? '/agents' : current === 'tools' ? '/tools' : current === 'open' || current === 'inspectHeader' || current === 'inspectStatus' || current === 'inspectTabs' || current === 'practice' || current === 'use' || current === 'useComplete' ? '/skills' : current === 'publishEntry' || current === 'publishBasics' || current === 'publishCategory' || current === 'publishDocumentation' || current === 'publishScope' || current === 'publishSubmit' || current === 'manage' ? '/dashboard/resources' : '/' })
  }

  return (
    <>
      <button type="button" onClick={restartJourney} className="inline-flex h-9 w-9 items-center justify-center rounded-full transition-colors hover:bg-secondary hover:text-foreground" aria-label="重新开始新手引导" title="重新开始新手引导">
        <Compass className="h-5 w-5" strokeWidth={1.8} />
      </button>

      <Dialog open={isGoalOpen} onOpenChange={setIsGoalOpen}>
        <DialogContent className="max-w-xl rounded-xl p-0 overflow-hidden">
          <div className="bg-[#f6f8fa] px-7 py-8 md:px-10 md:py-10">
            <div className="inline-flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary"><Sparkles className="h-5 w-5" /></div>
            <DialogHeader className="mt-5 text-left">
              <DialogTitle className="text-2xl">{t('onboarding.welcome.title', { name: displayName ?? t('onboarding.member') })}</DialogTitle>
              <DialogDescription className="mt-2 text-base leading-7">{t('onboarding.welcome.description')}</DialogDescription>
            </DialogHeader>
          </div>
          <div className="grid gap-3 p-6 md:grid-cols-2 md:p-8">
            <button type="button" onClick={() => selectGoal('USE')} className="rounded-lg border bg-white p-5 text-left transition hover:border-primary hover:shadow-sm">
              <Search className="h-5 w-5 text-primary" /><h3 className="mt-4 font-semibold">{t('onboarding.welcome.useTitle')}</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{t('onboarding.welcome.useDescription')}</p>
              <span className="mt-4 inline-flex items-center text-sm font-medium text-primary">{t('onboarding.welcome.useAction')} <ChevronRight className="ml-1 h-4 w-4" /></span>
            </button>
            <button type="button" onClick={() => selectGoal('PUBLISH')} className="rounded-lg border bg-white p-5 text-left transition hover:border-primary hover:shadow-sm">
              <Send className="h-5 w-5 text-primary" /><h3 className="mt-4 font-semibold">{t('onboarding.welcome.publishTitle')}</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{t('onboarding.welcome.publishDescription')}</p>
              <span className="mt-4 inline-flex items-center text-sm font-medium text-primary">{t('onboarding.welcome.publishAction')} <ChevronRight className="ml-1 h-4 w-4" /></span>
            </button>
          </div>
          <button type="button" onClick={() => { chooseOnboardingGoal(userId, 'USE'); setIsGoalOpen(false) }} className="pb-6 text-center text-sm text-muted-foreground hover:text-foreground">{t('onboarding.welcome.explore')}</button>
        </DialogContent>
      </Dialog>

    </>
  )
}
