import type { SkillSummary } from '@/api/types'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Bookmark, Boxes, Check, Copy, Download, Star } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { useStar } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { useSkillRepositories } from '@/shared/hooks/use-skill-repositories'
import { resolveRepositoryDisplayName } from '@/shared/lib/repository-display'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { cn } from '@/shared/lib/utils'
import { buildInstallCommand, getBaseUrl } from './install-command'
import { completeOnboardingJourneyUse } from '@/features/onboarding/onboarding-progress'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
  density?: 'default' | 'discovery' | 'list'
  showVersion?: boolean
}

/** Reusable, readable Skill summary card used across discovery pages. */
export function SkillCard({ skill, onClick, highlightStarred = true, density = 'default', showVersion = true }: SkillCardProps) {
  const { t } = useTranslation()
  const { isAuthenticated, user } = useAuth()
  const { data: repositories } = useSkillRepositories()
  const { data: starStatus } = useStar(skill.id, highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starStatus?.starred
  const headlineVersion = getHeadlineVersion(skill)
  const isInteractive = typeof onClick === 'function'
  const [copied, copy] = useCopyToClipboard()
  const quickInstall = () => {
    completeOnboardingJourneyUse(user?.userId)
    void copy(buildInstallCommand(skill.namespace, skill.slug, getBaseUrl()))
  }

  if (density === 'list') {
    return <Card className="group h-full cursor-pointer rounded-md border bg-white shadow-none transition hover:border-primary/50 hover:shadow-sm" onClick={onClick} onKeyDown={(event) => { if (isInteractive && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); onClick() } }} role={isInteractive ? 'link' : undefined} tabIndex={isInteractive ? 0 : undefined}>
      <div className="flex min-h-40 flex-col p-4">
        <div className="flex min-w-0 items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-violet-50 text-violet-600"><Boxes className="h-[18px] w-[18px]" /></span>
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-base font-semibold leading-5 group-hover:text-primary">{skill.localizedDisplayName || skill.displayName}</h3>
            <p className="mt-1 truncate text-xs text-muted-foreground">@{skill.namespace}/{skill.slug}</p>
          </div>
          <NamespaceBadge type="TEAM" name={resolveRepositoryDisplayName(skill.namespace, repositories)} className="max-w-28 shrink-0 truncate whitespace-nowrap" />
        </div>
        <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">{skill.localizedSummary || skill.summary || t('skillCard.noSummary')}</p>
        <div className="mt-auto flex items-center justify-between gap-3 border-t border-border/60 pt-3 text-xs">
          <div className="flex items-center gap-3 text-muted-foreground"><span className="inline-flex items-center gap-1"><Download className="h-3.5 w-3.5" />{formatCompactCount(skill.downloadCount)}</span><span className="inline-flex items-center gap-1"><Bookmark className="h-3.5 w-3.5" />{skill.starCount}</span></div>
          <div className="flex shrink-0 items-center gap-3 font-medium text-primary"><button type="button" onClick={(event) => { event.stopPropagation(); quickInstall() }} className="inline-flex items-center gap-1 hover:underline">{copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}{copied ? t('copyButton.copied') : '复制安装'}</button><span className="inline-flex items-center gap-1">详情 <ArrowRight className="h-3.5 w-3.5" /></span></div>
        </div>
      </div>
    </Card>
  }

  return (
    <Card
      className={cn(
        'group relative h-full cursor-pointer overflow-hidden border bg-white shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2',
        density === 'discovery' ? 'p-5' : 'p-5',
      )}
      style={{ borderColor: 'hsl(var(--border-card))' }}
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      <div className="flex h-full flex-col">
        <div className="mb-4 flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex min-w-0 items-center gap-2">
              <span className="rounded-md bg-violet-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">
                {t('skillCard.type')}
              </span>
              <div className="min-w-0 flex-1">
                <h3 className="truncate text-lg font-semibold transition-colors group-hover:text-primary" style={{ color: 'hsl(var(--foreground))' }}>
                  {skill.localizedDisplayName || skill.displayName}
                </h3>
                {skill.localizedDisplayName && skill.localizedDisplayName !== skill.displayName ? (
                  <p className="truncate text-xs text-muted-foreground">{skill.displayName}</p>
                ) : null}
              </div>
            </div>
          </div>
          <NamespaceBadge type="TEAM" name={resolveRepositoryDisplayName(skill.namespace, repositories)} className="max-w-28 shrink-0 truncate whitespace-nowrap" />
        </div>

        <div className={cn('mb-5', density === 'discovery' && 'min-h-[5.75rem]')}>
          <p className="text-xs font-semibold text-muted-foreground">{t('skillCard.whatItDoes')}</p>
          <p className={cn('mt-1 text-sm leading-6 text-foreground/80', density === 'discovery' ? 'line-clamp-2' : 'line-clamp-3')}>
            {skill.localizedSummary || skill.summary || t('skillCard.noSummary')}
          </p>
        </div>

        <div className={cn('mt-auto flex items-center justify-between gap-3 text-xs text-muted-foreground', density === 'discovery' && 'border-t border-border/60 pt-3')}>
          <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
            {showVersion && headlineVersion ? (
              <span
                className="max-w-32 truncate rounded-full bg-secondary/60 px-2.5 py-1 font-mono"
                title={`v${headlineVersion.version}`}
              >
                v{headlineVersion.version}
              </span>
            ) : null}
            <span className="flex items-center gap-1" title={t('skillCard.downloads')}>
              <Download className="h-3.5 w-3.5" aria-hidden="true" />
              {formatCompactCount(skill.downloadCount)}
            </span>
            <span className={`flex items-center gap-1 ${showStarredHighlight ? 'font-semibold text-primary' : ''}`} title={t('skillCard.stars')}>
              <Bookmark className={`h-3.5 w-3.5 ${showStarredHighlight ? 'fill-current' : ''}`} aria-hidden="true" />
              {skill.starCount}
            </span>
            {skill.ratingAvg !== undefined && skill.ratingCount > 0 ? (
              <span className="flex items-center gap-1" title={t('skillCard.rating')}>
                <Star className="h-3.5 w-3.5 fill-current text-primary" aria-hidden="true" />
                {skill.ratingAvg.toFixed(1)}
              </span>
            ) : null}
          </div>
          <span className="flex shrink-0 items-center gap-3 whitespace-nowrap">
            <button type="button" onClick={(event) => { event.stopPropagation(); quickInstall() }} className="inline-flex items-center gap-1 font-medium text-primary hover:underline">
              {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}{copied ? t('copyButton.copied') : '复制安装'}
            </button>
            <span className="inline-flex items-center gap-1 font-medium text-primary">{t('skillCard.viewDetails')}<ArrowRight className="h-3.5 w-3.5" aria-hidden="true" /></span>
          </span>
        </div>
      </div>
    </Card>
  )
}
