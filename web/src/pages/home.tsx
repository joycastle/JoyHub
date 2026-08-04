import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type SkillSort = 'newest' | 'downloads'

/** Skill discovery center. The product landing page is intentionally kept separate. */
export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SkillSort>('newest')
  const { data, isLoading, isError, isFetching } = useSearchSkills({
    q: query,
    sort,
    size: 48,
  })

  const handleSearch = (value: string) => {
    setQuery(normalizeSearchQuery(value))
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <section className="rounded-3xl border border-violet-200/60 bg-gradient-to-br from-violet-100/80 via-background to-sky-100/60 px-7 py-12 md:px-12">
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

        <div className="mt-8 max-w-2xl">
          <SearchBar
            placeholder={t('skillCenter.searchPlaceholder')}
            isSearching={isFetching && !isLoading}
            onSearch={handleSearch}
          />
        </div>
      </section>

      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-1 text-sm font-medium text-muted-foreground">{t('skillCenter.sortLabel')}</span>
        <Button variant={sort === 'newest' ? 'default' : 'outline'} size="sm" onClick={() => setSort('newest')}>
          {t('skillCenter.newest')}
        </Button>
        <Button variant={sort === 'downloads' ? 'default' : 'outline'} size="sm" onClick={() => setSort('downloads')}>
          {t('skillCenter.popular')}
        </Button>
        {!isLoading && data ? (
          <span className="ml-auto text-sm text-muted-foreground">{t('skillCenter.resultCount', { count: data.total })}</span>
        ) : null}
      </div>

      {isLoading ? <SkeletonList count={9} /> : null}
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
    </div>
  )
}
