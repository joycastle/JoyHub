import { useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ResourceCenterShell } from '@/features/search/resource-center-shell'
import { useUnifiedResourceSearch } from '@/features/search/use-unified-resource-search'
import { SkillCard } from '@/features/skill/skill-card'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { ResourceCategorySelect } from '@/shared/components/resource-category-select'
import { ResourceResultGrid } from '@/shared/components/resource-result-grid'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useViewMode } from '@/shared/hooks/use-view-mode'
import type { ResourceCategoryCode } from '@/shared/lib/resource-category'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { completeOnboardingTask, openOnboardingGuide } from '@/features/onboarding/onboarding-progress'
import { useAuth } from '@/features/auth/use-auth'

type CenterSort = 'relevance' | 'newest' | 'downloads'
const PAGE_SIZE = 12

/** Skill discovery center, using the same discovery frame as Agent and Tool. */
export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { onboarding } = useSearch({ from: '/skills' })
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<CenterSort>('newest')
  const [page, setPage] = useState(0)
  const [categoryCode, setCategoryCode] = useState<ResourceCategoryCode>()
  const [viewMode, setViewMode] = useViewMode('skills')
  const [highlightedTarget, setHighlightedTarget] = useState<CenterTourTarget | null>(null)
  const { data, isLoading, isError, isFetching } = useUnifiedResourceSearch({
    q: query,
    type: 'SKILL',
    categoryCode,
    sort,
    page,
    size: PAGE_SIZE,
  })
  const skills = (data?.items ?? []).flatMap((item) => item.skill ? [item.skill] : [])
  const pageCount = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1
  return (
    <ResourceCenterShell
      eyebrow={t('resourceCenter.eyebrow')}
      title={t('skillCenter.title')}
      description={t('skillCenter.description')}
      visibility={t('skillCenter.visibility')}
      publishLabel={t('skillCenter.publish')}
      onPublish={() => navigate({ to: '/dashboard/publish', search: onboarding ? { onboarding: true } : {} })}
      queryInput={queryInput}
      onQueryChange={setQueryInput}
      onSearch={(value) => {
        setQueryInput(value)
        setQuery(normalizeSearchQuery(value))
        setPage(0)
      }}
      searchPlaceholder={t('skillCenter.searchPlaceholder')}
      isSearching={isFetching && !isLoading}
      resultCount={data?.total}
      resultCountLabel={t('resourceCenter.resultCount', { count: data?.total ?? 0 })}
      viewMode={viewMode}
      onViewModeChange={setViewMode}
      highlightedTarget={highlightedTarget}
      filters={(
        <>
          <ResourceCategorySelect
            value={categoryCode}
            onChange={(value) => { setCategoryCode(value); setPage(0) }}
            className="w-full sm:w-56"
            triggerPrefix={`${t('resourceCategory.label')}：`}
          />
          <Select value={sort} onValueChange={(value) => { setSort(value as CenterSort); setPage(0) }}>
            <SelectTrigger className="w-full sm:w-44">
              <span>{t('resourceCenter.sortLabel')}：{t(`resourceCenter.sort.${sort}`)}</span>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="relevance">{t('resourceCenter.sort.relevance')}</SelectItem>
              <SelectItem value="newest">{t('resourceCenter.sort.newest')}</SelectItem>
              <SelectItem value="downloads">{t('resourceCenter.sort.downloads')}</SelectItem>
            </SelectContent>
          </Select>
        </>
      )}
    >
      {isLoading ? <SkeletonList count={6} /> : null}
      {isError ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-destructive">
          {t('resourceCenter.loadFailed')}
        </div>
      ) : null}
      {!isLoading && !isError && skills.length === 0 ? <EmptyState title={t('resourceCenter.empty')} /> : null}
      {!isLoading && !isError && skills.length > 0 ? (
        <div data-onboarding-target="catalog">
        <div data-onboarding-target="search-results">
        <ResourceResultGrid viewMode={viewMode}>
          {skills.map((skill) => (
            <div key={skill.id} className="h-full">
              <SkillCard
                skill={skill}
                density={viewMode === 'list' ? 'list' : 'default'}
                showVersion={viewMode === 'list'}
                onClick={() => navigate({ to: '/space/$namespace/$slug', params: { namespace: skill.namespace, slug: skill.slug } })}
              />
            </div>
          ))}
        </ResourceResultGrid>
        </div>
        </div>
      ) : null}
      {!isLoading && !isError && data && pageCount > 1 ? (
        <Pagination page={page} totalPages={pageCount} onPageChange={setPage} />
      ) : null}
      {onboarding ? <CenterFeatureTour
        center="SKILL"
        hasCatalogItems={skills.length > 0}
        onTargetChange={setHighlightedTarget}
        onComplete={() => completeOnboardingTask(user?.userId, 'skills')}
        onDismiss={() => navigate({ to: '/skills', search: {} })}
        onReturnToOnboarding={openOnboardingGuide}
      /> : null}
    </ResourceCenterShell>
  )
}
