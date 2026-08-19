import { useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { CatalogCenter, CatalogResourceKind } from '@/api/types'
import { resourcesApi } from '@/api/client'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { useCommonTools } from '@/features/catalog/common-tools'
import { ResourceCenterShell } from '@/features/search/resource-center-shell'
import { useUnifiedResourceSearch } from '@/features/search/use-unified-resource-search'
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
import { completeOnboardingJourneyUse, completeOnboardingTask, openOnboardingGuide } from '@/features/onboarding/onboarding-progress'
import { useAuth } from '@/features/auth/use-auth'

type CenterSort = 'relevance' | 'newest' | 'downloads'
const PAGE_SIZE = 12

function CatalogCenterPage({ center }: { center: CatalogCenter }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const routePath = center === 'AGENT' ? '/agents' : '/tools'
  const search = useSearch({ from: routePath })
  const { onboarding, q: query, sort, page } = search
  const categoryCode = search.categoryCode as ResourceCategoryCode | undefined
  const isAgent = center === 'AGENT'
  const translationKey = isAgent ? 'agentCenter' : 'toolCenter'
  const publishKind: CatalogResourceKind = isAgent ? 'AGENT' : 'ONLINE_TOOL'
  const [queryInput, setQueryInput] = useState(query)
  const [viewMode, setViewMode] = useViewMode(`catalog-${center.toLowerCase()}`)
  const [highlightedTarget, setHighlightedTarget] = useState<CenterTourTarget | null>(null)
  const { isCommonTool, recordToolUse, toggleTool } = useCommonTools()
  const { data, isLoading, isError, isFetching } = useUnifiedResourceSearch({
    q: query,
    type: isAgent ? 'AGENT' : 'TOOL',
    categoryCode,
    sort,
    page,
    size: PAGE_SIZE,
  })
  const resources = (data?.items ?? []).flatMap((item) => item.catalogResource ? [item.catalogResource] : [])
  const pageCount = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1
  return (
    <ResourceCenterShell
      eyebrow={t('resourceCenter.eyebrow')}
      title={t(`${translationKey}.title`)}
      description={t(`${translationKey}.description`)}
      visibility={t('resourceCenter.visibility')}
      publishLabel={t(`${translationKey}.publish`)}
      onPublish={() => navigate({ to: '/dashboard/catalog/new', search: onboarding ? { kind: publishKind, onboarding: true } : { kind: publishKind } })}
      queryInput={queryInput}
      onQueryChange={setQueryInput}
      onSearch={(value) => {
        const q = normalizeSearchQuery(value)
        setQueryInput(q)
        navigate({ to: routePath, search: { ...search, q, page: 0 } })
      }}
      searchPlaceholder={t(`${translationKey}.searchPlaceholder`)}
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
            onChange={(value) => navigate({ to: routePath, search: { ...search, categoryCode: value, page: 0 } })}
            className="w-full sm:w-56"
            triggerPrefix={`${t('resourceCategory.label')}：`}
          />
          <Select value={sort} onValueChange={(value) => navigate({ to: routePath, search: { ...search, sort: value as CenterSort, page: 0 } })}>
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
      {!isLoading && !isError && resources.length === 0 ? <EmptyState title={t('resourceCenter.empty')} /> : null}
      {!isLoading && !isError && resources.length > 0 ? (
        <div data-onboarding-target="catalog">
        <ResourceResultGrid viewMode={viewMode}>
          {resources.map((resource) => (
            <div key={resource.id} className="h-full">
              <CatalogResourceCard
                resource={resource}
                variant={viewMode === 'list' ? 'list' : 'default'}
                onClick={() => navigate({
                  to: '/catalog/$slug',
                  params: { slug: resource.slug },
                  search: { returnTo: `${window.location.pathname}${window.location.search}` },
                })}
                onUse={resource.accessUrl
                  ? () => { completeOnboardingJourneyUse(user?.userId); if (!isAgent) recordToolUse(resource.id); window.open(resource.accessUrl, '_blank', 'noopener,noreferrer') }
                  : resource.artifactAvailable
                    ? () => { completeOnboardingJourneyUse(user?.userId); if (!isAgent) recordToolUse(resource.id); window.open(resourcesApi.downloadUrl(`catalog:${resource.id}`), '_blank', 'noopener,noreferrer') }
                    : undefined}
                quickActionLabel={resource.accessUrl
                  ? t(isAgent ? 'agentCenter.use' : 'toolCenter.use')
                  : resource.artifactAvailable ? t('toolCenter.download') : undefined}
                isCommonTool={!isAgent && isCommonTool(resource.id)}
                onToggleCommonTool={!isAgent ? () => toggleTool(resource.id) : undefined}
              />
            </div>
          ))}
        </ResourceResultGrid>
        </div>
      ) : null}
      {!isLoading && !isError && data && pageCount > 1 ? (
        <Pagination page={page} totalPages={pageCount} onPageChange={(nextPage) => navigate({ to: routePath, search: { ...search, page: nextPage } })} />
      ) : null}
      {onboarding ? <CenterFeatureTour
        center={center}
        hasCatalogItems={resources.length > 0}
        onTargetChange={setHighlightedTarget}
        onComplete={() => completeOnboardingTask(user?.userId, isAgent ? 'agents' : 'tools')}
        onDismiss={() => navigate({ to: routePath, search: { ...search, onboarding: undefined } })}
        onReturnToOnboarding={openOnboardingGuide}
      /> : null}
    </ResourceCenterShell>
  )
}

export function AgentsPage() {
  return <CatalogCenterPage center="AGENT" />
}

export function ToolsPage() {
  return <CatalogCenterPage center="TOOL" />
}
