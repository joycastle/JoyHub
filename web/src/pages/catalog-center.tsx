import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'
import type { CatalogCenter, CatalogResourceKind } from '@/api/types'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { CATALOG_RESOURCE_KINDS, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResources } from '@/features/catalog/use-catalog-queries'
import { useCommonTools } from '@/features/catalog/common-tools'
import { namespaceApi, resourcesApi } from '@/api/client'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { resumePlatformOnboarding } from '@/features/onboarding/onboarding-events'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { ViewModeToggle } from '@/shared/components/view-mode-toggle'
import { useViewMode } from '@/shared/hooks/use-view-mode'

function CatalogCenterPage({ center, showArrivalGuide }: { center: CatalogCenter; showArrivalGuide: boolean }) {
  const navigate = useNavigate()
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [kind, setKind] = useState<CatalogResourceKind | undefined>()
  const [scenario, setScenario] = useState('')
  const [departmentId, setDepartmentId] = useState<number | undefined>()
  const [sort, setSort] = useState<'recommended' | 'newest'>('recommended')
  const [isArrivalGuideVisible, setIsArrivalGuideVisible] = useState(showArrivalGuide)
  const [tourTarget, setTourTarget] = useState<CenterTourTarget | null>(null)
  const [isSearchPinned, setIsSearchPinned] = useState(false)
  const searchDockRef = useRef<HTMLFormElement>(null)
  const isAgent = center === 'AGENT'
  const [viewMode, setViewMode] = useViewMode(`catalog-${center.toLowerCase()}`)
  const { isCommonTool, recordToolUse, toggleTool } = useCommonTools()
  const { data: departments = [] } = useQuery({ queryKey: ['namespaces', 'mine'], queryFn: () => namespaceApi.listMine() })
  const { data, isLoading, isError } = useCatalogResources({
    center,
    q: query,
    kind,
    scenario: scenario || undefined,
    departmentId,
    sort: isAgent ? sort : undefined,
    size: 48,
  })
  const { data: allCenterData } = useCatalogResources({ center, size: 100 })
  const availableKinds = isAgent ? ['AGENT'] as CatalogResourceKind[] : CATALOG_RESOURCE_KINDS.filter((item) => item !== 'AGENT')
  const publishKind: CatalogResourceKind = isAgent ? 'AGENT' : (kind ?? 'ONLINE_TOOL')
  const publishLabel = isAgent ? '发布 Agent' : `发布${kind ? catalogKindLabel(kind) : '工具'}`
  const resources = useMemo(() => data?.items ?? [], [data?.items])
  const scenarios = useMemo(
    () => Array.from(new Set((allCenterData?.items ?? []).flatMap((resource) => resource.scenarios ?? []))).sort((left, right) => left.localeCompare(right, 'zh-CN')),
    [allCenterData?.items],
  )
  const selectedKindLabel = kind ? catalogKindLabel(kind) : '全部'
  const selectedScenarioLabel = scenario || '全部'
  const selectedDepartmentLabel = departmentId
    ? departments.find((department) => department.id === departmentId)?.displayName ?? '全部'
    : '全部'
  const isCatalogHighlighted = tourTarget === 'catalog'

  useEffect(() => {
    setIsArrivalGuideVisible(showArrivalGuide)
    if (!showArrivalGuide) {
      setTourTarget(null)
    }
  }, [showArrivalGuide])

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
      <section className="border-b border-border bg-[#f6f8fa] px-1 pb-8 pt-5 md:px-2">
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
          <div className="max-w-3xl space-y-4">
            <div className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">CAPABILITY MARKETPLACE</div>
            <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">{isAgent ? 'Agent 中心' : '工具中心'}</h1>
            <p className="text-base leading-7 text-muted-foreground">
              {isAgent
                ? '发现公司内部可直接使用的 AI Agent、机器人和自动化助手。'
                : '按工作场景发现在线工具、插件、MCP、内部服务和资源包。'}
            </p>
            <p className="text-sm text-muted-foreground">这里仅展示全公司可见，以及你所在部门可见的已发布内容。</p>
          </div>
          <aside className="border-l border-border pl-6" data-onboarding-target="quickBrowse">
            <Button
              className={cn('w-full rounded-md shadow-none', tourTarget === 'publish' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
              data-onboarding-target="publish"
              onClick={() => navigate({ to: '/dashboard/catalog/new', search: { kind: publishKind } })}
            >
              <Plus className="mr-2 h-4 w-4" />
              {publishLabel}
            </Button>
            <div className="mt-5 border-t border-border pt-4">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">快速浏览</p>
              <p className="mt-2 text-sm font-semibold text-foreground">{isAgent ? '飞书机器人与自动化助手' : '在线工具、插件与资源包'}</p>
              <p className="mt-1 text-xs leading-5 text-muted-foreground">{isAgent ? '从卡片直接进入会话，或先查看使用说明。' : '按工具类型和工作场景筛选，快速找到可用入口。'}</p>
            </div>
          </aside>
        </div>
        <form
          ref={searchDockRef}
          className={cn('mt-7 flex max-w-2xl gap-3 rounded-md', tourTarget === 'search' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
          data-onboarding-target="search"
          onSubmit={(event) => {
            event.preventDefault()
            setQuery(queryInput.trim())
          }}
        >
          <div className="relative flex-1">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input
              value={queryInput}
              onChange={(event) => setQueryInput(event.target.value)}
              className="bg-background pl-10"
              placeholder={isAgent ? '搜索 Agent、场景或能力' : '搜索工具、场景或类型'}
            />
          </div>
          <Button type="submit">搜索</Button>
        </form>
      </section>

      <section className={cn('fixed inset-x-0 top-16 z-40 border-b border-border bg-white/95 shadow-sm backdrop-blur transition-all duration-200', isSearchPinned ? 'translate-y-0 opacity-100' : '-translate-y-full invisible pointer-events-none opacity-0')} aria-label="快捷搜索">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-5 py-3 md:px-10">
          <form
            className="flex min-w-0 flex-1 gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              setQuery(queryInput.trim())
            }}
          >
            <div className="relative min-w-0 flex-1"><Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} className="bg-white pl-10" placeholder={isAgent ? '搜索 Agent、场景或能力' : '搜索工具、场景或类型'} /></div>
            <Button type="submit" className="rounded-md shadow-none">搜索</Button>
          </form>
          <Button variant="outline" className="hidden shrink-0 rounded-md shadow-none lg:inline-flex" onClick={() => navigate({ to: '/dashboard/catalog/new', search: { kind: publishKind } })}><Plus className="mr-1.5 h-4 w-4" />{publishLabel}</Button>
        </div>
      </section>

      <div className="mt-8 grid gap-8 lg:grid-cols-[12rem_minmax(0,1fr)]">
        <aside className="h-fit border-r border-border pr-5 lg:sticky lg:top-6">
          <p className="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{isAgent ? '浏览 Agent' : '浏览工具'}</p>
          <button
            type="button"
            onClick={() => { setKind(undefined); setScenario('') }}
            className={cn('relative block w-full rounded-md px-2.5 py-2 text-left text-sm font-medium transition-colors', kind === undefined && !scenario ? 'bg-slate-100 text-foreground before:absolute before:-left-[22px] before:top-1.5 before:h-6 before:w-1 before:rounded-full before:bg-primary' : 'text-muted-foreground hover:bg-slate-100')}
          >
            {isAgent ? '全部 Agent' : '全部工具'}
          </button>
          {!isAgent ? <>
            <div className="my-3 border-t" />
            <label htmlFor="catalog-kind-filter" className="block px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">工具类型</label>
            <Select value={kind ?? 'ALL'} onValueChange={(value) => { setKind(value === 'ALL' ? undefined : value as CatalogResourceKind); setScenario('') }}>
              <SelectTrigger id="catalog-kind-filter"><span>工具类型：{selectedKindLabel}</span></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部类型</SelectItem>
                {availableKinds.map((item) => <SelectItem key={item} value={item}>{catalogKindLabel(item)}</SelectItem>)}
              </SelectContent>
            </Select>
          </> : null}
          <div className="my-3 border-t" />
          <label htmlFor="catalog-scenario-filter" className="block px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">工作场景</label>
          <Select value={scenario || 'ALL'} onValueChange={(value) => setScenario(value === 'ALL' ? '' : value)}>
            <SelectTrigger id="catalog-scenario-filter"><span>工作场景：{selectedScenarioLabel}</span></SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">全部场景</SelectItem>
              {scenarios.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
            </SelectContent>
          </Select>
        </aside>

        <main className="min-w-0">
          <div
            className={cn('mb-6 flex flex-wrap items-center gap-2 border-b pb-5', tourTarget === 'filters' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target="filters"
          >
            <span className="mr-1 text-sm font-medium text-muted-foreground">进一步筛选</span>
            <Select value={scenario || 'ALL'} onValueChange={(value) => setScenario(value === 'ALL' ? '' : value)}>
              <SelectTrigger className="w-48"><span>适用场景：{selectedScenarioLabel}</span></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部场景</SelectItem>
                {scenarios.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={departmentId?.toString() ?? 'ALL'} onValueChange={(value) => setDepartmentId(value === 'ALL' ? undefined : Number(value))}>
              <SelectTrigger className="w-48"><span>可见范围：{selectedDepartmentLabel}</span></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部范围</SelectItem>
                {departments.map((department) => <SelectItem key={department.id} value={department.id.toString()}>{department.displayName}</SelectItem>)}
              </SelectContent>
            </Select>
            {isAgent ? <>
              <span className="ml-2 mr-1 text-sm font-medium text-muted-foreground">排序</span>
              <Button variant={sort === 'recommended' ? 'default' : 'outline'} size="sm" className="rounded-md shadow-none" onClick={() => setSort('recommended')}>推荐</Button>
              <Button variant={sort === 'newest' ? 'default' : 'outline'} size="sm" className="rounded-md shadow-none" onClick={() => setSort('newest')}>最新</Button>
            </> : null}
            <ViewModeToggle value={viewMode} onChange={setViewMode} className="ml-auto" />
          </div>

          {isLoading ? <div className="py-20 text-center text-muted-foreground">正在加载...</div> : null}
          {isError ? <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-destructive">加载失败，请稍后重试。</div> : null}
          {!isLoading && !isError && resources.length === 0 ? (
            <div className="flex justify-center">
              <div className="w-full max-w-md rounded-lg border border-dashed bg-slate-50 p-12 text-center text-muted-foreground"><img src="/joycastle-icon.png" alt="" className="mx-auto mb-4 h-12 w-12 opacity-50" />暂无匹配内容</div>
            </div>
          ) : null}
          <div className={cn('grid gap-3', viewMode === 'list' ? 'grid-cols-1 xl:grid-cols-2' : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3')}>
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
                    : resource.artifactAvailable ? () => { if (!isAgent) recordToolUse(resource.id); window.open(resourcesApi.downloadUrl(`catalog:${resource.id}`), '_blank', 'noopener,noreferrer') } : undefined}
                  quickActionLabel={resource.accessUrl ? (resource.kind === 'AGENT' ? '在飞书中使用' : '立即使用') : resource.artifactAvailable ? '下载' : undefined}
                  isCommonTool={!isAgent && isCommonTool(resource.id)}
                  onToggleCommonTool={!isAgent ? () => toggleTool(resource.id) : undefined}
                />
              </div>
            ))}
          </div>
        </main>
      </div>
      {isArrivalGuideVisible ? (
        <CenterFeatureTour
          center={center}
          hasCatalogItems={resources.length > 0}
          onDismiss={dismissArrivalGuide}
          onReturnToOnboarding={resumePlatformOnboarding}
          onTargetChange={setTourTarget}
        />
      ) : null}
    </div>
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
