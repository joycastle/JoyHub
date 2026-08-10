import { useEffect, useRef, useState } from 'react'
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
  const [isSearchPinned, setIsSearchPinned] = useState(false)
  const searchDockRef = useRef<HTMLDivElement>(null)
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

  useEffect(() => {
    const updatePinnedState = () => setIsSearchPinned((searchDockRef.current?.getBoundingClientRect().bottom ?? Number.POSITIVE_INFINITY) <= 72)
    updatePinnedState()
    window.addEventListener('scroll', updatePinnedState, { passive: true })
    return () => window.removeEventListener('scroll', updatePinnedState)
  }, [])

  const dismissArrivalGuide = () => {
    setTourTarget(null)
    setIsArrivalGuideVisible(false)
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <section className="border-b bg-[#f6f8fa] px-1 pb-8 pt-5 md:px-2">
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
          <div className="max-w-3xl space-y-4">
            <div className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">CAPABILITY MARKETPLACE</div>
            <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">{t('skillCenter.title')}</h1>
            <p className="text-base leading-7 text-muted-foreground">{t('skillCenter.description')}</p>
            <p className="text-sm text-muted-foreground">{t('skillCenter.visibility')}</p>
          </div>
          <aside className="border-l border-border pl-6" data-onboarding-target="quickBrowse">
            <Button
              size="lg"
              className={cn('w-full rounded-md shadow-none', tourTarget === 'publish' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
              data-onboarding-target="publish"
              onClick={() => navigate({ to: '/dashboard/publish' })}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('skillCenter.publish')}
            </Button>
            <div className="mt-5 border-t border-border pt-4">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">快速浏览</p>
              <p className="mt-2 text-sm font-semibold text-foreground">可复制、可复用的工作方法</p>
              <p className="mt-1 text-xs leading-5 text-muted-foreground">先查看能力说明，再一键复制安装到你的工作环境。</p>
            </div>
          </aside>
        </div>

        <div
          ref={searchDockRef}
          className={cn('mt-7 max-w-2xl rounded-md', tourTarget === 'search' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
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

      <section className={cn('fixed inset-x-0 top-16 z-40 border-b border-border bg-white/95 shadow-sm backdrop-blur transition-all duration-200', isSearchPinned ? 'translate-y-0 opacity-100' : '-translate-y-full invisible pointer-events-none opacity-0')} aria-label="快捷搜索">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-5 py-3 md:px-10">
          <form
            className="flex min-w-0 flex-1 gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              handleSearch(queryInput)
            }}
          >
            <div className="relative min-w-0 flex-1"><Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} className="bg-white pl-10" placeholder={t('skillCenter.searchPlaceholder')} /></div>
            <Button type="submit" className="rounded-md shadow-none" disabled={isFetching && !isLoading}>搜索</Button>
          </form>
          <Button variant="outline" className="hidden shrink-0 rounded-md shadow-none lg:inline-flex" onClick={() => navigate({ to: '/dashboard/publish' })}><Plus className="mr-1.5 h-4 w-4" />{t('skillCenter.publish')}</Button>
        </div>
      </section>

      <div className="mt-8 grid gap-8 lg:grid-cols-[12rem_minmax(0,1fr)]">
        <aside className="h-fit border-r border-border pr-5 lg:sticky lg:top-6">
          <p className="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">精选</p>
          <button type="button" onClick={() => { setSort('newest'); setPage(0) }} className={cn('relative block w-full rounded-md px-2.5 py-2 text-left text-sm transition-colors', sort === 'newest' ? 'bg-slate-100 font-semibold text-foreground before:absolute before:-left-[22px] before:top-1.5 before:h-6 before:w-1 before:rounded-full before:bg-primary' : 'text-muted-foreground hover:bg-slate-100')}>最近上新</button>
          <button type="button" onClick={() => { setSort('downloads'); setPage(0) }} className={cn('relative block w-full rounded-md px-2.5 py-2 text-left text-sm transition-colors', sort === 'downloads' ? 'bg-slate-100 font-semibold text-foreground before:absolute before:-left-[22px] before:top-1.5 before:h-6 before:w-1 before:rounded-full before:bg-primary' : 'text-muted-foreground hover:bg-slate-100')}>下载热榜</button>
        </aside>
        <main className="min-w-0">
      <div
        className={cn('flex flex-wrap items-center gap-2 border-b pb-5', tourTarget === 'filters' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
        data-onboarding-target="filters"
      >
        <span className="mr-1 text-sm font-medium text-muted-foreground">{t('skillCenter.sortLabel')}</span>
        <Button variant={sort === 'newest' ? 'default' : 'outline'} size="sm" className="rounded-md shadow-none" onClick={() => { setSort('newest'); setPage(0) }}>
          {t('skillCenter.newest')}
        </Button>
        <Button variant={sort === 'downloads' ? 'default' : 'outline'} size="sm" className="rounded-md shadow-none" onClick={() => { setSort('downloads'); setPage(0) }}>
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
          <div className="w-full max-w-md rounded-lg border border-dashed bg-slate-50 p-12 text-center text-muted-foreground"><img src="/joycastle-icon.png" alt="" className="mx-auto mb-4 h-12 w-12 opacity-50" />
            {t('skillCenter.empty')}
          </div>
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {skills.map((skill, index) => (
          <div
            key={skill.id}
            className={cn(`h-full animate-fade-up delay-${Math.min(index % 6 + 1, 6)}`, isCatalogHighlighted && index === 0 && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target={isCatalogHighlighted && index === 0 ? 'catalog' : undefined}
          >
            <SkillCard
              skill={skill}
              density="list"
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
        </main>
      </div>
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
