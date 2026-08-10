import { startTransition, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Bot, Boxes, Loader2, Puzzle, Sparkles, Wrench } from 'lucide-react'
import type { UnifiedResourceSearchType } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { useUnifiedResourceSearch } from '@/features/search/use-unified-resource-search'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { formatNamespaceSearchInput, normalizeSearchQuery, parseNamespaceSearchInput } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12
type SearchResourceType = UnifiedResourceSearchType

const RESOURCE_TYPES: Array<{ type: SearchResourceType; labelKey: string; icon: typeof Boxes }> = [
  { type: 'ALL', labelKey: 'search.types.all', icon: Boxes },
  { type: 'AGENT', labelKey: 'search.types.agent', icon: Bot },
  { type: 'TOOL', labelKey: 'search.types.tool', icon: Wrench },
  { type: 'SKILL', labelKey: 'search.types.skill', icon: Puzzle },
]

const SCENARIO_QUERIES = [
  { labelKey: 'search.scenarios.writing', query: '文档 内容 总结' },
  { labelKey: 'search.scenarios.data', query: '数据 分析 报表' },
  { labelKey: 'search.scenarios.development', query: '研发 代码 测试' },
  { labelKey: 'search.scenarios.project', query: '项目 管理 协作' },
  { labelKey: 'search.scenarios.design', query: '设计 美术 素材' },
] as const

function blurActiveElement() {
  if (typeof document === 'undefined' || typeof HTMLElement === 'undefined') {
    return
  }

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
}

function scrollToTopOnPageChange() {
  if (typeof window === 'undefined') {
    return () => {}
  }

  let secondFrame = 0
  const firstFrame = window.requestAnimationFrame(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
    secondFrame = window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'auto' })
    })
  })

  return () => {
    window.cancelAnimationFrame(firstFrame)
    if (secondFrame) {
      window.cancelAnimationFrame(secondFrame)
    }
  }
}

/**
 * Skill discovery page with synchronized URL state.
 *
 * Search text, sorting, pagination, and the starred-only filter are mirrored into router search
 * params so the page can be shared, restored, and revisited without losing state.
 */
export function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated } = useAuth()

  const q = normalizeSearchQuery(searchParams.q || '')
  const namespace = (searchParams.namespace || '').replace(/^@/, '')
  const selectedLabel = searchParams.label || ''
  const sort = searchParams.sort || 'newest'
  const page = searchParams.page ?? 0
  const starredOnly = searchParams.starredOnly ?? false
  const resourceType = searchParams.type ?? 'ALL'
  const [queryInput, setQueryInput] = useState(formatNamespaceSearchInput(namespace, q))
  const previousPageRef = useRef(page)

  useEffect(() => {
    setQueryInput(formatNamespaceSearchInput(namespace, q))
  }, [namespace, q])

  useEffect(() => {
    if (previousPageRef.current !== page) {
      blurActiveElement()
      const cleanupScroll = scrollToTopOnPageChange()

      previousPageRef.current = page
      return () => {
        cleanupScroll()
      }
    }

    previousPageRef.current = page
  }, [page])

  const { data: unifiedResults, isLoading, isFetching } = useUnifiedResourceSearch({
    q,
    namespace: namespace || undefined,
    label: selectedLabel || undefined,
    sort,
    type: resourceType,
    starredOnly,
    page,
    size: PAGE_SIZE,
  }, !starredOnly || isAuthenticated)
  const { data: labels } = useVisibleLabels()
  useEffect(() => {
    // Debounce URL updates while the user is typing so query state stays shareable without
    // triggering a navigation on every keystroke.
    const parsedInput = parseNamespaceSearchInput(queryInput)
    if (parsedInput.query === q && parsedInput.namespace === namespace) {
      return
    }

    if (!parsedInput.query && !parsedInput.namespace) {
      startTransition(() => {
        navigate({ to: '/search', search: { q: '', namespace: '', label: selectedLabel, sort, page: 0, starredOnly, type: resourceType }, replace: page === 0 })
      })
      return
    }

    const timeoutId = window.setTimeout(() => {
      startTransition(() => {
        navigate({ to: '/search', search: { q: parsedInput.query, namespace: parsedInput.namespace, label: selectedLabel, sort, page: 0, starredOnly, type: resourceType }, replace: true })
      })
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [navigate, namespace, page, q, queryInput, resourceType, selectedLabel, sort, starredOnly])

  const handleSearch = (query: string) => {
    const parsedInput = parseNamespaceSearchInput(query)
    setQueryInput(query)
    startTransition(() => {
      navigate({ to: '/search', search: { q: parsedInput.query, namespace: parsedInput.namespace, label: selectedLabel, sort, page: 0, starredOnly, type: resourceType }, replace: true })
    })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, sort: newSort, page: 0, starredOnly, type: resourceType } })
  }

  const handlePageChange = (newPage: number) => {
    blurActiveElement()
    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, sort, page: newPage, starredOnly, type: resourceType } })
  }

  const handleLabelToggle = (label: string) => {
    const nextLabel = selectedLabel === label ? '' : label
    navigate({ to: '/search', search: { q, namespace, label: nextLabel, sort, page: 0, starredOnly, type: resourceType } })
  }

  const handleNamespaceClear = () => {
    navigate({ to: '/search', search: { q, namespace: '', label: selectedLabel, sort, page: 0, starredOnly, type: resourceType } })
  }

  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({
        to: '/login',
        search: {
          returnTo: `${window.location.pathname}${window.location.search}${window.location.hash}`,
        },
      })
      return
    }

    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, sort, page: 0, starredOnly: !starredOnly, type: resourceType } })
  }

  const handleResourceTypeChange = (type: SearchResourceType) => {
    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, sort, page: 0, starredOnly, type } })
  }

  const handleScenarioSearch = (query: string) => {
    setQueryInput(query)
    navigate({ to: '/search', search: { q: query, namespace: '', label: '', sort: 'relevance', page: 0, starredOnly: false, type: resourceType } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}`, search: { returnTo: `${window.location.pathname}${window.location.search}` } })
  }

  const totalPages = unifiedResults ? Math.ceil(unifiedResults.total / unifiedResults.size) : 0
  const isPageLoading = isLoading
  const isUpdatingResults = isFetching && !isLoading
  const resultCount = unifiedResults?.total ?? 0
  const unifiedItems = unifiedResults?.items ?? []
  const hasAnyResults = unifiedItems.length > 0

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <section className="rounded-3xl border border-primary/15 bg-gradient-to-br from-primary/10 via-background to-violet-100/50 px-6 py-9 md:px-10">
        <div className="mx-auto max-w-3xl text-center">
          <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
            <Sparkles className="h-3.5 w-3.5" /> JoyHub 2.0
          </div>
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">{t('search.title')}</h1>
          <p className="mt-2 text-muted-foreground">{t('search.subtitle')}</p>
        </div>
        <div className="mx-auto mt-7 max-w-3xl">
        <SearchBar
          value={queryInput}
          placeholder={t('search.placeholder')}
          isSearching={isUpdatingResults}
          onChange={setQueryInput}
          onSearch={handleSearch}
        />
        </div>
        <div className="mx-auto mt-5 flex max-w-3xl flex-wrap items-center justify-center gap-2">
          <span className="text-xs font-medium text-muted-foreground">{t('search.scenarios.label')}</span>
          {SCENARIO_QUERIES.map((scenario) => (
            <Button key={scenario.labelKey} type="button" variant="ghost" size="sm" onClick={() => handleScenarioSearch(scenario.query)}>
              {t(scenario.labelKey)}
            </Button>
          ))}
        </div>
      </section>

      <div className="flex flex-wrap gap-2 rounded-2xl border bg-card p-2">
        {RESOURCE_TYPES.map(({ type, labelKey, icon: Icon }) => (
          <Button key={type} type="button" variant={resourceType === type ? 'default' : 'ghost'} onClick={() => handleResourceTypeChange(type)}>
            <Icon className="mr-2 h-4 w-4" />
            {t(labelKey)}
          </Button>
        ))}
      </div>

      {/* Sort And Filters */}
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
            <div className="flex gap-2">
              <Button
                variant={sort === 'relevance' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('relevance')}
              >
                {t('search.sort.relevance')}
              </Button>
              <Button
                variant={sort === 'downloads' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('downloads')}
              >
                {t('search.sort.downloads')}
              </Button>
              <Button
                variant={sort === 'newest' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('newest')}
              >
                {t('search.sort.newest')}
              </Button>
            </div>
          </div>

          {resultCount > 0 && (
            <div className="text-sm text-muted-foreground">
              {t('search.results', { count: resultCount })}
            </div>
          )}
        </div>

        {isUpdatingResults ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>{t('search.loadingMore')}</span>
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-2">
          <span className="shrink-0 text-sm font-medium text-muted-foreground">{t('search.filters.label')}</span>
          <Button
            variant={starredOnly ? 'default' : 'outline'}
            size="sm"
            onClick={handleStarredToggle}
          >
            {t('search.filterStarred')}
          </Button>
          {!starredOnly && labels?.map((label) => (
            <Button
              key={label.slug}
              variant={selectedLabel === label.slug ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleLabelToggle(label.slug)}
            >
              {label.displayName}
            </Button>
          ))}
          {namespace ? (
            <Button
              variant="default"
              size="sm"
              onClick={handleNamespaceClear}
            >
              {t('search.namespaceFilter', { namespace })}
            </Button>
          ) : null}
        </div>
      </div>

      {/* Results */}
      {!isAuthenticated && resourceType !== 'SKILL' ? (
        <div className="rounded-2xl border border-dashed bg-secondary/20 p-5 text-center text-sm text-muted-foreground">{t('search.loginForInternal')}</div>
      ) : null}

      {isPageLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : hasAnyResults ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {unifiedItems.map((item, idx) => {
                  const animationClass = `h-full animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`
                  const skill = item.skill
                  if (item.resourceType === 'SKILL' && skill) {
                    return (
                      <div key={`SKILL:${skill.id}`} className={animationClass}>
                        <SkillCard
                          skill={skill}
                          highlightStarred
                          onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                        />
                      </div>
                    )
                  }
                  const resource = item.catalogResource
                  if (resource) {
                    return (
                      <div key={`${item.resourceType}:${resource.id}`} className={animationClass}>
                        <CatalogResourceCard
                          resource={resource}
                          onClick={() => navigate({
                            to: '/catalog/$slug',
                            params: { slug: resource.slug },
                          })}
                        />
                      </div>
                    )
                  }
                  return null
                })}
          </div>
          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={handlePageChange}
            />
          )}
        </>
      ) : (
        <EmptyState
          title={starredOnly ? t('search.noStarredResults') : t('search.noResults')}
          description={
            starredOnly
              ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills'))
              : (q ? t('search.noResultsFor', { q }) : undefined)
          }
        />
      )}
    </div>
  )
}
