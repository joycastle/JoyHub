import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  ArrowRight,
  Bookmark,
  Bot,
  Boxes,
  Briefcase,
  Code2,
  Database,
  Download,
  Palette,
  Puzzle,
  Sparkles,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { SearchBar } from '@/features/search/search-bar'
import { useResourceRecommendations, useUnifiedResourceSearch } from '@/features/search/use-unified-resource-search'
import { useAuth } from '@/features/auth/use-auth'
import { namespaceApi, resourcesApi } from '@/api/client'
import type { UnifiedResourceSearchItem, UnifiedResourceSearchType } from '@/api/types'
import { resourceCategoryLabel, type ResourceCategoryCode } from '@/shared/lib/resource-category'
import { ResourceCategorySelect } from '@/shared/components/resource-category-select'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { cn } from '@/shared/lib/utils'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'
import { buildInstallCommand, getBaseUrl } from '@/features/skill/install-command'
import { useCommonTools } from '@/features/catalog/common-tools'
import { ViewModeToggle } from '@/shared/components/view-mode-toggle'
import { useViewMode } from '@/shared/hooks/use-view-mode'
import { completeOnboardingJourneyUse, completeOnboardingTask, openOnboardingGuide } from '@/features/onboarding/onboarding-progress'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'

type DiscoveryMode = 'recommended' | 'downloads' | 'newest'
type HomeScopeFilter = 'ALL' | 'PUBLIC' | 'DEPARTMENT'

const DISCOVERY_ITEMS: Array<{ key: DiscoveryMode; label: string }> = [
  { key: 'recommended', label: '为你和所在部门推荐' },
  { key: 'downloads', label: '下载热榜' },
  { key: 'newest', label: '最近上新' },
]

const DISCOVERY_TITLES: Record<DiscoveryMode, string> = {
  recommended: '为你和所在部门推荐',
  downloads: '下载热榜',
  newest: '最近上新',
}

const DISCOVERY_FILTER_LABELS: Record<DiscoveryMode, string> = {
  recommended: '为你推荐',
  downloads: '下载最多',
  newest: '最新发布',
}

const SCENARIOS: Array<{ code: ResourceCategoryCode; icon: LucideIcon }> = [
  { code: 'CREATIVE_MEDIA', icon: Palette },
  { code: 'DATA_ANALYTICS', icon: Database },
  { code: 'COLLAB_PRODUCTIVITY', icon: Briefcase },
  { code: 'GAME_DEV_QA', icon: Code2 },
  { code: 'UA_MONETIZATION', icon: Sparkles },
]

const RESOURCE_TYPES: Array<{ type: UnifiedResourceSearchType; labelKey: string; icon: LucideIcon }> = [
  { type: 'ALL', labelKey: 'search.types.all', icon: Boxes },
  { type: 'AGENT', labelKey: 'search.types.agent', icon: Bot },
  { type: 'TOOL', labelKey: 'search.types.tool', icon: Wrench },
  { type: 'SKILL', labelKey: 'search.types.skill', icon: Puzzle },
]

const QUICK_BROWSE_ITEMS: Array<{ to: '/agents' | '/skills' | '/tools'; label: string; description: string; icon: LucideIcon }> = [
  { to: '/agents', label: 'Agent 中心', description: '直接在飞书中使用', icon: Bot },
  { to: '/skills', label: '技能中心', description: '复制安装，沉淀方法', icon: Puzzle },
  { to: '/tools', label: '工具中心', description: '下载或打开工具', icon: Wrench },
]

function RecommendationCard({ resource, onOpen, onToolUse }: { resource: UnifiedResourceSearchItem; onOpen: () => void; onToolUse?: (toolId: number) => void }) {
  const catalog = resource.catalogResource
  const skill = resource.skill
  const [copied, copy] = useCopyToClipboard()
  const title = catalog?.name || skill?.localizedDisplayName || skill?.displayName || '未命名能力'
  const identifier = catalog?.slug || (skill ? `@${skill.namespace}/${skill.slug}` : '')
  const summary = catalog?.summary || skill?.localizedSummary || skill?.summary || '暂未提供能力说明。'
  const Icon = resource.resourceType === 'AGENT' ? Bot : resource.resourceType === 'TOOL' ? Wrench : Boxes
  const iconClassName = resource.resourceType === 'AGENT'
    ? 'bg-blue-50 text-blue-600'
    : resource.resourceType === 'TOOL'
      ? 'bg-emerald-50 text-emerald-600'
      : 'bg-violet-50 text-violet-600'
  const quickActionLabel = skill
    ? copied ? '已复制' : '复制安装'
    : catalog?.accessUrl
      ? catalog.kind === 'AGENT' ? '立即使用' : '打开工具'
      : catalog?.artifactAvailable ? '下载' : null
  const { user } = useAuth()
  const handleQuickAction = () => {
    completeOnboardingJourneyUse(user?.userId)
    if (catalog && catalog.kind !== 'AGENT') onToolUse?.(catalog.id)
    if (skill) {
      void copy(buildInstallCommand(skill.namespace, skill.slug, getBaseUrl()))
      return
    }
    if (catalog?.accessUrl) {
      window.open(catalog.accessUrl, '_blank', 'noopener,noreferrer')
      return
    }
    if (catalog?.artifactAvailable) {
      window.open(resourcesApi.downloadUrl(`catalog:${catalog.id}`), '_blank', 'noopener,noreferrer')
    }
  }

  return (
    <div
      role="link"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onOpen()
        }
      }}
      className="group flex min-h-40 flex-col rounded-md border border-border bg-white p-4 text-left shadow-none transition hover:border-primary/50 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
    >
      <div className="flex min-w-0 items-start gap-3">
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-md ${iconClassName}`}>
          {catalog?.icon ? <span className="text-lg">{catalog.icon}</span> : <Icon className="h-[18px] w-[18px]" aria-hidden="true" />}
        </span>
        <div className="min-w-0 pt-0.5">
          <h3 className="truncate text-base font-semibold leading-5 text-foreground transition-colors group-hover:text-primary">{title}</h3>
          <p className="mt-1 truncate font-mono text-xs text-muted-foreground">{identifier}</p>
        </div>
        <span className="ml-auto shrink-0 rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
          {resource.resourceType === 'AGENT' ? 'Agent' : resource.resourceType === 'TOOL' ? '工具' : 'Skill'}
        </span>
      </div>
      <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">{summary}</p>
      <div className="mt-auto flex items-center justify-between gap-3 border-t border-border/60 pt-3 text-xs">
        <span className="flex items-center gap-3 text-muted-foreground">
          <span className="inline-flex items-center gap-1"><Download className="h-3.5 w-3.5" />{skill?.downloadCount ?? 0}</span>
          <span className="inline-flex items-center gap-1"><Bookmark className="h-3.5 w-3.5" />{skill?.starCount ?? 0}</span>
        </span>
        <span className="flex shrink-0 items-center gap-3">
          {quickActionLabel ? <button type="button" onClick={(event) => { event.stopPropagation(); handleQuickAction() }} className="font-medium text-primary hover:underline">{quickActionLabel}</button> : null}
          <span className="inline-flex items-center gap-1 font-medium text-primary">查看详情 <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" /></span>
        </span>
      </div>
    </div>
  )
}

/** JoyHub product home: a unified starting point for every internal AI capability. */
export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { onboarding } = useSearch({ from: '/' })
  const { isAuthenticated, user } = useAuth()
  const [discoveryMode, setDiscoveryMode] = useState<DiscoveryMode>('recommended')
  const [homeResourceType, setHomeResourceType] = useState<UnifiedResourceSearchType>('ALL')
  const [homeScenario, setHomeScenario] = useState<ResourceCategoryCode | undefined>()
  const [homeScopeFilter, setHomeScopeFilter] = useState<HomeScopeFilter>('ALL')
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchType, setSearchType] = useState<UnifiedResourceSearchType>('ALL')
  const [searchSort, setSearchSort] = useState<'relevance' | 'downloads' | 'newest'>('relevance')
  const [isQuickSearchPinned, setIsQuickSearchPinned] = useState(false)
  const [highlightedTarget, setHighlightedTarget] = useState<CenterTourTarget | null>(null)
  const { toolIds: commonToolIds, recordToolUse } = useCommonTools()
  const [viewMode, setViewMode] = useViewMode('home')
  const quickSearchRef = useRef<HTMLDivElement>(null)
  const { data: recommendations = [] } = useResourceRecommendations(12)
  const { data: myNamespaces = [] } = useQuery({ queryKey: ['namespaces', 'mine'], queryFn: () => namespaceApi.listMine() })
  const { data: rankedResources } = useUnifiedResourceSearch(
    {
      q: '',
      categoryCode: homeScenario,
      sort: discoveryMode === 'downloads' ? 'downloads' : 'newest',
      page: 0,
      size: 12,
    },
    discoveryMode !== 'recommended',
  )
  const { data: toolResources } = useUnifiedResourceSearch({
    q: '',
    type: 'TOOL',
    sort: 'downloads',
    page: 0,
    size: 100,
  })
  const { data: scenarioResources } = useUnifiedResourceSearch(
    {
      q: '',
      categoryCode: homeScenario,
      sort: discoveryMode === 'downloads' ? 'downloads' : discoveryMode === 'newest' ? 'newest' : 'relevance',
      page: 0,
      size: 24,
    },
    Boolean(homeScenario),
  )
  const { data: searchResults, isFetching: isSearching } = useUnifiedResourceSearch(
    {
      q: searchQuery,
      sort: searchSort,
      type: searchType,
      page: 0,
      size: 12,
    },
    Boolean(searchQuery),
  )
  const discoveryResources = useMemo(
    () => homeScenario
      ? scenarioResources?.items ?? []
      : discoveryMode === 'recommended'
      ? recommendations.map(({ resource }) => resource)
      : rankedResources?.items ?? [],
    [discoveryMode, homeScenario, rankedResources?.items, recommendations, scenarioResources?.items],
  )
  const visibleDiscoveryResources = discoveryResources.filter((resource) => {
    if (homeResourceType !== 'ALL' && resource.resourceType !== homeResourceType) return false
    if (homeScopeFilter === 'PUBLIC') {
      return resource.skill?.namespace === 'global' || resource.catalogResource?.department == null
    }
    if (homeScopeFilter === 'DEPARTMENT') {
      return (resource.catalogResource?.department?.id != null && myNamespaces.some((namespace) => namespace.id === resource.catalogResource?.department?.id))
        || (resource.skill?.namespace != null && myNamespaces.some((namespace) => namespace.slug === resource.skill?.namespace))
    }
    return true
  })
  const commonTools = useMemo(() => {
    const toolsById = new Map((toolResources?.items ?? []).flatMap((resource) => resource.catalogResource ? [[resource.catalogResource.id, resource] as const] : []))
    return commonToolIds.map((id) => toolsById.get(id)).filter((resource): resource is UnifiedResourceSearchItem => resource != null)
  }, [commonToolIds, toolResources?.items])
  const homeScopeLabel = homeScopeFilter === 'PUBLIC'
    ? '公司公共库'
    : homeScopeFilter === 'DEPARTMENT' ? '所在部门' : '全部'

  const handleSearch = (query: string) => {
    const normalizedQuery = normalizeSearchQuery(query)
    setSearchInput(normalizedQuery)
    setSearchQuery(normalizedQuery)
    setSearchSort('relevance')
    if (normalizedQuery.trim()) completeOnboardingTask(user?.userId, 'discover')
  }

  const searchScenario = (categoryCode: ResourceCategoryCode) => {
    setHomeScenario(categoryCode)
    setSearchQuery('')
    completeOnboardingTask(user?.userId, 'discover')
  }

  useEffect(() => {
    const updatePinnedState = () => setIsQuickSearchPinned((quickSearchRef.current?.getBoundingClientRect().bottom ?? Number.POSITIVE_INFINITY) <= 72)
    updatePinnedState()
    window.addEventListener('scroll', updatePinnedState, { passive: true })
    return () => window.removeEventListener('scroll', updatePinnedState)
  }, [])

  return (
    <div className="relative z-10">
      <section className="border-b border-border bg-[#f6f8fa] px-5 py-9 md:px-10">
        <div className="mx-auto max-w-7xl">
          <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
            <div className="max-w-4xl">
              <div className="mb-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary">JOYHUB MARKETPLACE</div>
              <h1 className="text-3xl font-semibold tracking-tight text-foreground md:text-[2.5rem]">
                {t('joyhubHome.title')}
              </h1>
              <p className="mt-2 text-muted-foreground">
                {t('search.subtitle')}
              </p>
              <div ref={quickSearchRef} data-onboarding-target="search" className={cn(highlightedTarget === 'search' && 'relative z-50 rounded-lg ring-4 ring-primary/50 ring-offset-4')}>
                <div className="mt-6 max-w-4xl">
                  <SearchBar
                    value={searchInput}
                    placeholder={t('search.placeholder')}
                    isSearching={isSearching}
                    onChange={setSearchInput}
                    onSearch={handleSearch}
                  />
                </div>
                <div data-onboarding-target="filters" className={cn('mt-4 flex max-w-6xl flex-nowrap items-center gap-2 overflow-x-auto pb-1', highlightedTarget === 'filters' && 'relative z-50 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4')}>
                  <span className="shrink-0 text-xs font-medium text-muted-foreground">{t('search.scenarios.label')}</span>
                  <button
                    type="button"
                    onClick={() => { setHomeScenario(undefined); setSearchQuery('') }}
                    className={cn('inline-flex shrink-0 items-center gap-2 rounded-md border bg-white px-3 py-2 text-sm font-medium transition-colors', !homeScenario ? 'border-primary text-primary' : 'border-border hover:border-primary/50 hover:text-primary')}
                  >
                    {t('resourceCategory.allOption')}
                  </button>
                  {SCENARIOS.map((scenario) => {
                    const Icon = scenario.icon
                    return (
                      <button
                        key={scenario.code}
                        type="button"
                        onClick={() => searchScenario(scenario.code)}
                        className="group inline-flex shrink-0 items-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-medium transition-colors hover:border-primary/50 hover:text-primary"
                      >
                        <Icon className="h-4 w-4 text-primary" />
                        <span>{resourceCategoryLabel(t, scenario.code)}</span>
                        <ArrowRight className="h-3.5 w-3.5 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>
            <aside data-onboarding-target="quickBrowse" className={cn('border-l border-border pl-6 lg:mt-1', highlightedTarget === 'quickBrowse' && 'relative z-50 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4')}>
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">快速浏览</p>
              <div className="mt-3 divide-y divide-border">
                {QUICK_BROWSE_ITEMS.map(({ to, label, description, icon: Icon }) => (
                  <Link key={to} to={to} className="group flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                    <span className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-primary shadow-sm ring-1 ring-border"><Icon className="h-4 w-4" /></span>
                    <span className="min-w-0 flex-1"><span className="block text-sm font-semibold text-foreground group-hover:text-primary">{label}</span><span className="mt-0.5 block text-xs text-muted-foreground">{description}</span></span>
                    <ArrowRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                  </Link>
                ))}
              </div>
            </aside>
          </div>
        </div>
      </section>

      <section className={cn('fixed inset-x-0 top-16 z-40 border-b border-border bg-white/95 shadow-sm backdrop-blur transition-all duration-200', isQuickSearchPinned ? 'translate-y-0 opacity-100' : '-translate-y-full invisible pointer-events-none opacity-0')} aria-label="快捷搜索">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-5 py-3 md:px-10">
          <div className="min-w-0 flex-1"><SearchBar value={searchInput} placeholder={t('search.placeholder')} isSearching={isSearching} onChange={setSearchInput} onSearch={handleSearch} /></div>
          <div className="hidden items-center gap-2 lg:flex">
            <button type="button" onClick={() => { setHomeScenario(undefined); setSearchQuery('') }} className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border bg-white px-2.5 py-2 text-xs font-medium transition-colors', !homeScenario ? 'border-primary text-primary' : 'border-border text-muted-foreground hover:border-primary/50 hover:text-primary')}>
              {t('resourceCategory.allOption')}
            </button>
            {SCENARIOS.map((scenario) => {
              const Icon = scenario.icon
              return <button key={scenario.code} type="button" onClick={() => searchScenario(scenario.code)} className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border border-border bg-white px-2.5 py-2 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/50 hover:text-primary"><Icon className="h-3.5 w-3.5" />{resourceCategoryLabel(t, scenario.code)}</button>
            })}
          </div>
        </div>
      </section>

      {!searchQuery ? (
        <section className="border-b bg-white px-5 py-7 md:px-10" aria-labelledby="common-tools-heading">
          <div className="mx-auto max-w-7xl">
            <div className="mb-4 flex items-end justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-primary">TOOLS</p>
                <h2 id="common-tools-heading" className="mt-1 text-2xl font-semibold">{t('joyhubHome.commonTools.title')}</h2>
              </div>
              <Link to="/tools" className="shrink-0 text-sm font-medium text-primary hover:underline">
                {t('joyhubHome.commonTools.browseAll')} <ArrowRight className="inline h-4 w-4" aria-hidden="true" />
              </Link>
            </div>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {commonTools.map((resource) => {
                const catalog = resource.catalogResource
                if (!catalog) return null
                return (
                  <RecommendationCard
                    key={`COMMON_TOOL:${catalog.id}`}
                    resource={resource}
                    onOpen={() => navigate({ to: '/catalog/$slug', params: { slug: catalog.slug } })}
                    onToolUse={recordToolUse}
                  />
                )
              })}
              {commonTools.length === 0 ? <div className="rounded-md border border-dashed border-border bg-[#f6f8fa] px-5 py-8 text-sm text-muted-foreground md:col-span-2 xl:col-span-4">暂未添加常用工具。可在工具中心点击“添加到常用工具”，或在同一工具打开、下载 3 次后自动加入。</div> : null}
            </div>
          </div>
        </section>
      ) : null}

      {searchQuery ? (
        <section className="border-b bg-[#f6f8fa] px-5 py-8 md:px-10">
          <div className="mx-auto max-w-7xl space-y-6">
            <div className="flex flex-wrap gap-2 border-b pb-4">
              {RESOURCE_TYPES.map(({ type, labelKey, icon: Icon }) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setSearchType(type)}
                  className={`inline-flex items-center rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ${searchType === type ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-white hover:border-primary/50 hover:text-primary'}`}
                >
                  <Icon className="mr-2 h-4 w-4" />
                  {t(labelKey)}
                </button>
              ))}
            </div>
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
                <div className="flex gap-2">
                  {(['relevance', 'downloads', 'newest'] as const).map((sort) => (
                    <button
                      key={sort}
                      type="button"
                      onClick={() => setSearchSort(sort)}
                      className={`rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ${searchSort === sort ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-white hover:border-primary/50'}`}
                    >
                      {t(`search.sort.${sort}`)}
                    </button>
                  ))}
                </div>
              </div>
              <div className="ml-auto flex items-center gap-3"><span className="text-sm text-muted-foreground">{t('search.results', { count: searchResults?.total ?? 0 })}</span><ViewModeToggle value={viewMode} onChange={setViewMode} /></div>
            </div>
            {searchResults?.items.length ? (
              <div data-onboarding-target="search-results" className={cn('grid gap-3', viewMode === 'list' ? 'grid-cols-1 xl:grid-cols-2' : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3')}>
                {searchResults.items.map((resource) => {
                  const catalog = resource.catalogResource
                  const skill = resource.skill
                  if (skill) {
                    return <RecommendationCard
                      key={`SKILL:${skill.id}`}
                      resource={resource}
                      onOpen={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}` })}
                    />
                  }
                  if (catalog) {
                    return <RecommendationCard
                      key={`CATALOG:${catalog.id}`}
                      resource={resource}
                      onOpen={() => navigate({ to: '/catalog/$slug', params: { slug: catalog.slug } })}
                    />
                  }
                  return null
                })}
              </div>
            ) : !isSearching ? (
              <p className="rounded-md border bg-white px-5 py-10 text-center text-sm text-muted-foreground">{t('search.noResultsFor', { q: searchQuery })}</p>
            ) : null}
          </div>
        </section>
      ) : null}

      {!searchQuery ? <section className="border-b bg-[#f6f8fa] px-5 py-8 md:px-10">
        <div className="mx-auto grid max-w-7xl gap-9 lg:grid-cols-[13rem_minmax(0,1fr)]">
          <aside className="h-fit border-r border-border pr-5 lg:sticky lg:top-6">
            <p className="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">精选</p>
            <button type="button" onClick={() => { setDiscoveryMode('recommended'); setHomeResourceType('ALL') }} className={cn('relative block w-full rounded-md px-2.5 py-2 text-left text-sm transition-colors', discoveryMode === 'recommended' ? 'bg-slate-100 font-semibold text-foreground before:absolute before:-left-[13px] before:top-1.5 before:h-6 before:w-1 before:rounded-full before:bg-primary' : 'text-muted-foreground hover:bg-slate-100')}>为你推荐</button>
            <div className="my-3 border-t" />
            <p className="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">能力类型</p>
            {RESOURCE_TYPES.map(({ type, labelKey, icon: Icon }) => <button key={type} type="button" onClick={() => setHomeResourceType(type)} className={cn('flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm transition-colors', homeResourceType === type ? 'bg-slate-100 font-semibold text-foreground' : 'text-muted-foreground hover:bg-slate-100')}><Icon className="h-4 w-4 text-primary" />{t(labelKey)}</button>)}
            <div className="my-3 border-t" /><p className="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">发现</p>
            {DISCOVERY_ITEMS.map((item) => <button key={item.key} type="button" onClick={() => setDiscoveryMode(item.key)} className={`relative block w-full rounded-md px-2.5 py-2 text-left text-sm transition-colors ${discoveryMode === item.key ? 'bg-slate-100 font-semibold text-foreground before:absolute before:-left-[13px] before:top-1.5 before:h-6 before:w-1 before:rounded-full before:bg-primary' : 'text-muted-foreground hover:bg-slate-100'}`} aria-pressed={discoveryMode === item.key}>{item.label}</button>)}
          </aside>
          <div>
          <div className="mb-5 flex items-end justify-between gap-4">
            <div><p className="text-sm font-semibold text-primary">DISCOVER</p><h2 className="mt-1 text-2xl font-semibold">{discoveryMode === 'recommended' && !isAuthenticated ? '推荐能力' : DISCOVERY_TITLES[discoveryMode]}</h2></div>
            <div className="flex items-center gap-3"><ViewModeToggle value={viewMode} onChange={setViewMode} /><Link to="/search" search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }} className="text-sm font-medium text-primary">浏览全部 →</Link></div>
          </div>
          <div className="mb-5 flex flex-wrap items-center gap-2 border-b pb-4">
            <span className="mr-1 text-sm font-medium text-muted-foreground">进一步筛选</span>
            <label className="sr-only" htmlFor="home-scenario-filter">使用场景</label>
            <ResourceCategorySelect id="home-scenario-filter" value={homeScenario} onChange={setHomeScenario} className="w-48" triggerPrefix="使用场景：" />
            <label className="sr-only" htmlFor="home-scope-filter">可见范围</label>
            <Select value={homeScopeFilter} onValueChange={(value) => setHomeScopeFilter(value as HomeScopeFilter)}>
              <SelectTrigger id="home-scope-filter" className="w-48"><span>可见范围：{homeScopeLabel}</span></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部</SelectItem>
                <SelectItem value="PUBLIC">公司公共库</SelectItem>
                <SelectItem value="DEPARTMENT">所在部门</SelectItem>
              </SelectContent>
            </Select>
            <label className="sr-only" htmlFor="home-sort-filter">排序</label>
            <Select value={discoveryMode} onValueChange={(value) => setDiscoveryMode(value as DiscoveryMode)}>
              <SelectTrigger id="home-sort-filter" className="w-48"><span>排序：{DISCOVERY_FILTER_LABELS[discoveryMode]}</span></SelectTrigger>
              <SelectContent>
                <SelectItem value="recommended">为你推荐</SelectItem>
                <SelectItem value="newest">最新发布</SelectItem>
                <SelectItem value="downloads">下载最多</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div data-onboarding-target="catalog" className={cn('grid gap-3', viewMode === 'list' ? 'grid-cols-1 xl:grid-cols-2' : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3', highlightedTarget === 'catalog' && 'relative z-50 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4')}>
          {visibleDiscoveryResources.map((resource) => {
              const catalog = resource.catalogResource
              const skill = resource.skill
              if (skill) {
                return <div key={`SKILL:${skill.id}`}><RecommendationCard resource={resource} onOpen={() => { completeOnboardingTask(user?.userId, 'detail'); navigate({ to: '/space/$namespace/$slug', params: { namespace: skill.namespace, slug: skill.slug } }) }} /></div>
              }
              if (catalog) {
                return <div key={`CATALOG:${catalog.id}`}><RecommendationCard resource={resource} onOpen={() => { completeOnboardingTask(user?.userId, 'detail'); navigate({ to: '/catalog/$slug', params: { slug: catalog.slug } }) }} /></div>
              }
              return null
            })}
          </div>
          </div>
        </div>
      </section> : null}

      {onboarding ? <CenterFeatureTour
        center="LANDING"
        hasCatalogItems={visibleDiscoveryResources.length > 0}
        onTargetChange={setHighlightedTarget}
        onDismiss={() => navigate({ to: '/', search: {} })}
        onReturnToOnboarding={openOnboardingGuide}
      /> : null}


    </div>
  )
}
