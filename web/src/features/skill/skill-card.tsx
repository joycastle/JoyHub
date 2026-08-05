import type { SkillSummary } from '@/api/types'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Bookmark, Download, Star } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { useStar } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { useSkillRepositories } from '@/shared/hooks/use-skill-repositories'
import { resolveRepositoryDisplayName } from '@/shared/lib/repository-display'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
}

/** Reusable, readable Skill summary card used across discovery pages. */
export function SkillCard({ skill, onClick, highlightStarred = true }: SkillCardProps) {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const { data: repositories } = useSkillRepositories()
  const { data: starStatus } = useStar(skill.id, highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starStatus?.starred
  const headlineVersion = getHeadlineVersion(skill)
  const isInteractive = typeof onClick === 'function'

  return (
    <Card
      className="group relative h-full cursor-pointer overflow-hidden border bg-white p-5 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
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
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-violet-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">
                {t('skillCard.type')}
              </span>
              <div className="min-w-0">
                <h3 className="truncate text-lg font-semibold transition-colors group-hover:text-primary" style={{ color: 'hsl(var(--foreground))' }}>
                  {skill.localizedDisplayName || skill.displayName}
                </h3>
                {skill.localizedDisplayName && skill.localizedDisplayName !== skill.displayName ? (
                  <p className="truncate text-xs text-muted-foreground">{skill.displayName}</p>
                ) : null}
              </div>
            </div>
          </div>
          <NamespaceBadge type="TEAM" name={resolveRepositoryDisplayName(skill.namespace, repositories)} />
        </div>

        <div className="mb-5">
          <p className="text-xs font-semibold text-muted-foreground">{t('skillCard.whatItDoes')}</p>
          <p className="mt-1 line-clamp-3 text-sm leading-6 text-foreground/80">
            {skill.localizedSummary || skill.summary || t('skillCard.noSummary')}
          </p>
        </div>

        <div className="mt-auto flex items-center gap-3 text-xs text-muted-foreground">
          {headlineVersion ? (
            <span className="rounded-full bg-secondary/60 px-2.5 py-1 font-mono">v{headlineVersion.version}</span>
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
          <span className="ml-auto inline-flex items-center gap-1 font-medium text-primary">
            {t('skillCard.viewDetails')}
            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
          </span>
        </div>
      </div>
    </Card>
  )
}
