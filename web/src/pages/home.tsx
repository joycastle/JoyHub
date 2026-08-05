import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Plus, Search } from 'lucide-react'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type SkillSort = 'newest' | 'downloads'
const SKILL_PAGE_SIZE = 12

const SKILL_SCENARIOS = [
  { key: 'all', query: '' },
  { key: 'content', query: '内容生产' },
  { key: 'data', query: '数据分析' },
  { key: 'development', query: '研发提效' },
  { key: 'project', query: '项目管理' },
  { key: 'design', query: '设计与美术' },
] as const

/** Skill discovery center. The product landing page is intentionally kept separate. */
export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SkillSort>('newest')
  const [page, setPage] = useState(0)
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

  const chooseScenario = (value: string) => {
    setQueryInput(value)
    setQuery(normalizeSearchQuery(value))
    setPage(0)
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
          <Button size="lg" className="shrink-0" onClick={() => navigate({ to: '/dashboard/publish' })}>
            <Plus className="mr-2 h-4 w-4" />
            {t('skillCenter.publish')}
          </Button>
        </div>

        <form
          className="mt-8 flex max-w-2xl gap-3"
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
      </section>

      <div className="flex flex-wrap items-center gap-2">
        {SKILL_SCENARIOS.map((scenario) => (
          <Button
            key={scenario.key}
            variant={query === scenario.query ? 'default' : 'outline'}
            size="sm"
            onClick={() => chooseScenario(scenario.query)}
          >
            {t(`skillCenter.scenarios.${scenario.key}`)}
          </Button>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2">
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
      {!isLoading && !isError && data?.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed p-16 text-center text-muted-foreground">
          {t('skillCenter.empty')}
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
        {data?.items.map((skill, index) => (
          <div key={skill.id} className={`h-full animate-fade-up delay-${Math.min(index % 6 + 1, 6)}`}>
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
    </div>
  )
}
