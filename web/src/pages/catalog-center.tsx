import { useEffect, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { CatalogCenter, CatalogResourceKind } from '@/api/types'
import { resourcesApi } from '@/api/client'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { useCommonTools } from '@/features/catalog/common-tools'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { resumePlatformOnboarding } from '@/features/onboarding/onboarding-events'
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
import { cn } from '@/shared/lib/utils'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'

type CenterSort = 'relevance' | 'newest' | 'downloads'
const PAGE_SIZE = 12

function CatalogCenterPage({ center, showArrivalGuide }: { center: CatalogCenter; showArrivalGuide: boolean }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isAgent = center === 'AGENT'
  const translationKey = isAgent ? 'agentCenter' : 'toolCenter'
  const publishKind: CatalogResourceKind = isAgent ? 'AGENT' : 'ONLINE_TOOL'
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [categoryCode, setCategoryCode] = useState<ResourceCategoryCode>()
  const [sort, setSort] = useState<CenterSort>('relevance')
  const [page, setPage] = useState(0)
  const [isArrivalGuideVisible, setIsArrivalGuideVisible] = useState(showArrivalGuide)
  const [tourTarget, setTourTarget] = useState<CenterTourTarget | null>(null)
  const [viewMode, setViewMode] = useViewMode(`catalog-${center.toLowerCase()}`)
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
  const isCatalogHighlighted = tourTarget === 'catalog'

  useEffect(() => {
    setIsArrivalGuideVisible(showArrivalGuide)
    if (!showArrivalGuide) setTourTarget(null)
  }, [showArrivalGuide])

  const dismissArrivalGuide = () => {
    setTourTarget(null)
    setIsArrivalGuideVisible(false)
  }

  return (
    <ResourceCenterShell
      eyebrow={t('resourceCenter.eyebrow')}
      title={t(`${translationKey}.title`)}
      description={t(`${translationKey}.description`)}
      visibility={t('resourceCenter.visibility')}
      publishLabel={t(`${translationKey}.publish`)}
      onPublish={() => navigate({ to: '/dashboard/catalog/new', search: { kind: publishKind } })}
      queryInput={queryInput}
      onQueryChange={setQueryInput}
      onSearch={(value) => {
        setQueryInput(value)
        setQuery(normalizeSearchQuery(value))
        setPage(0)
      }}
      searchPlaceholder={t(`${translationKey}.searchPlaceholder`)}
      isSearching={isFetching && !isLoading}
      resultCount={data?.total}
      resultCountLabel={t('resourceCenter.resultCount', { count: data?.total ?? 0 })}
      viewMode={viewMode}
      onViewModeChange={setViewMode}
      highlightedTarget={tourTarget === 'publish' || tourTarget === 'search' || tourTarget === 'filters' ? tourTarget : null}
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
      {!isLoading && !isError && resources.length === 0 ? <EmptyState title={t('resourceCenter.empty')} /> : null}
      {!isLoading && !isError && resources.length > 0 ? (
        <ResourceResultGrid viewMode={viewMode}>
          {resources.map((resource, index) => (
            <div
              key={resource.id}
              className={cn('h-full', isCatalogHighlighted && index === 0 && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
              data-onboarding-target={isCatalogHighlighted && index === 0 ? 'catalog' : undefined}
            >
              <CatalogResourceCard
                resource={resource}
                variant={viewMode === 'list' ? 'list' : 'default'}
                onClick={() => navigate({ to: '/catalog/$slug', params: { slug: resource.slug } })}
                onUse={resource.accessUrl
                  ? () => { if (!isAgent) recordToolUse(resource.id); window.open(resource.accessUrl, '_blank', 'noopener,noreferrer') }
                  : resource.artifactAvailable
                    ? () => { if (!isAgent) recordToolUse(resource.id); window.open(resourcesApi.downloadUrl(`catalog:${resource.id}`), '_blank', 'noopener,noreferrer') }
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
      ) : null}
      {!isLoading && !isError && data && pageCount > 1 ? (
        <Pagination page={page} totalPages={pageCount} onPageChange={setPage} />
      ) : null}
      {isArrivalGuideVisible ? (
        <CenterFeatureTour
          center={center}
          hasCatalogItems={resources.length > 0}
          onDismiss={dismissArrivalGuide}
          onReturnToOnboarding={resumePlatformOnboarding}
          onTargetChange={setTourTarget}
        />
      ) : null}
    </ResourceCenterShell>
  )
}

export function AgentsPage() {
  const { onboarding } = useSearch({ from: '/agents' })
  return <CatalogCenterPage center="AGENT" showArrivalGuide={Boolean(onboarding)} />
}

export function ToolsPage() {
  const { onboarding } = useSearch({ from: '/tools' })
  return <CatalogCenterPage center="TOOL" showArrivalGuide={Boolean(onboarding)} />
}
