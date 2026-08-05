import { useEffect, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Plus, Search } from 'lucide-react'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { resumePlatformOnboarding } from '@/features/onboarding/onboarding-events'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { cn } from '@/shared/lib/utils'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type SkillSort = 'newest' | 'downloads'
const SKILL_PAGE_SIZE = 12

/** Skill discovery center. The product landing page is intentionally kept separate. */
export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { onboarding } = useSearch({ from: '/skills' })
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SkillSort>('newest')
  const [page, setPage] = useState(0)
  const [isArrivalGuideVisible, setIsArrivalGuideVisible] = useState(Boolean(onboarding))
  const [tourTarget, setTourTarget] = useState<CenterTourTarget | null>(null)
  const { data, isLoading, isError, isFetching } = useSearchSkills({
    q: query,
    sort,
    page,
    size: SKILL_PAGE_SIZE,
  })
  const pageCount = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1

  const handleSearch = (value: string) => {
    setQueryInput(value)
    setQuery(normalizeSearchQuery(value))
    setPage(0)
  }
  const skills = data?.items ?? []
  const isCatalogHighlighted = tourTarget === 'catalog'

  useEffect(() => {
    setIsArrivalGuideVisible(Boolean(onboarding))
    if (!onboarding) {
      setTourTarget(null)
    }
  }, [onboarding])

  const dismissArrivalGuide = () => {
    setTourTarget(null)
    setIsArrivalGuideVisible(false)
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <section className="rounded-3xl border border-primary/15 bg-gradient-to-br from-primary/10 via-background to-sky-100/50 px-7 py-12 md:px-12">
        <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
          <div className="max-w-3xl space-y-4">
            <div className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">JoyHub 2.0</div>
            <h1 className="text-4xl font-bold tracking-tight md:text-5xl">{t('skillCenter.title')}</h1>
            <p className="text-lg leading-8 text-muted-foreground">{t('skillCenter.description')}</p>
            <p className="text-sm text-muted-foreground">{t('skillCenter.visibility')}</p>
          </div>
          <Button
            size="lg"
            className={cn('shrink-0', tourTarget === 'publish' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target="publish"
            onClick={() => navigate({ to: '/dashboard/publish' })}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('skillCenter.publish')}
          </Button>
        </div>

        <div
          className={cn('mt-8 max-w-2xl rounded-xl', tourTarget === 'search' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
          data-onboarding-target="search"
        >
          <form
            className="flex gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              handleSearch(queryInput)
            }}
          >
            <div className="relative flex-1">
              <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                value={queryInput}
                onChange={(event) => setQueryInput(event.target.value)}
                className="bg-background pl-10"
                placeholder={t('skillCenter.searchPlaceholder')}
              />
            </div>
            <Button type="submit" disabled={isFetching && !isLoading}>{t('skillCenter.search')}</Button>
          </form>
        </div>
      </section>

      <div
        className={cn('flex flex-wrap items-center gap-2 rounded-xl', tourTarget === 'filters' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
        data-onboarding-target="filters"
      >
        <span className="mr-1 text-sm font-medium text-muted-foreground">{t('skillCenter.sortLabel')}</span>
        <Button variant={sort === 'newest' ? 'default' : 'outline'} size="sm" onClick={() => { setSort('newest'); setPage(0) }}>
          {t('skillCenter.newest')}
        </Button>
        <Button variant={sort === 'downloads' ? 'default' : 'outline'} size="sm" onClick={() => { setSort('downloads'); setPage(0) }}>
          {t('skillCenter.popular')}
        </Button>
        {!isLoading && data ? (
          <span className="ml-auto text-sm text-muted-foreground">{t('skillCenter.resultCount', { count: data.total })}</span>
        ) : null}
      </div>

      {isLoading ? <SkeletonList count={6} /> : null}
      {isError ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-destructive">
          {t('skillCenter.loadFailed')}
        </div>
      ) : null}
      {!isLoading && !isError && skills.length === 0 ? (
        <div className="flex justify-center">
          <div className="w-full max-w-md rounded-2xl border border-dashed p-12 text-center text-muted-foreground">
            {t('skillCenter.empty')}
          </div>
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
        {skills.map((skill, index) => (
          <div
            key={skill.id}
            className={cn(`h-full animate-fade-up delay-${Math.min(index % 6 + 1, 6)}`, isCatalogHighlighted && index === 0 && 'relative z-50 rounded-2xl ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target={isCatalogHighlighted && index === 0 ? 'catalog' : undefined}
          >
            <SkillCard
              skill={skill}
              onClick={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}` })}
            />
          </div>
        ))}
      </div>
      {!isLoading && !isError && data && pageCount > 1 ? (
        <nav className="flex items-center justify-center gap-3" aria-label={t('skillCenter.pagination')}>
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(current - 1, 0))}
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            {t('skillCenter.previous')}
          </Button>
          <span className="text-sm text-muted-foreground">
            {t('skillCenter.pageInfo', { current: page + 1, total: pageCount })}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= pageCount - 1}
            onClick={() => setPage((current) => Math.min(current + 1, pageCount - 1))}
          >
            {t('skillCenter.next')}
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </nav>
      ) : null}
      {isArrivalGuideVisible ? (
        <CenterFeatureTour
          center="SKILL"
          hasCatalogItems={skills.length > 0}
          onDismiss={dismissArrivalGuide}
          onReturnToOnboarding={resumePlatformOnboarding}
          onTargetChange={setTourTarget}
        />
      ) : null}
    </div>
  )
}
