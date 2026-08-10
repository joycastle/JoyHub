import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'
import type { CatalogCenter, CatalogResourceKind } from '@/api/types'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { CATALOG_RESOURCE_KINDS, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResources } from '@/features/catalog/use-catalog-queries'
import { namespaceApi } from '@/api/client'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { resumePlatformOnboarding } from '@/features/onboarding/onboarding-events'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

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
  const isAgent = center === 'AGENT'
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
  const availableKinds = isAgent ? ['AGENT'] as CatalogResourceKind[] : CATALOG_RESOURCE_KINDS.filter((item) => item !== 'AGENT')
  const publishKind: CatalogResourceKind = isAgent ? 'AGENT' : (kind ?? 'ONLINE_TOOL')
  const publishLabel = isAgent ? '发布 Agent' : `发布${kind ? catalogKindLabel(kind) : '工具'}`
  const resources = useMemo(() => data?.items ?? [], [data?.items])
  const scenarios = useMemo(
    () => Array.from(new Set(resources.flatMap((resource) => resource.scenarios ?? []))).sort((left, right) => left.localeCompare(right, 'zh-CN')),
    [resources],
  )
  const isCatalogHighlighted = tourTarget === 'catalog'

  useEffect(() => {
    setIsArrivalGuideVisible(showArrivalGuide)
    if (!showArrivalGuide) {
      setTourTarget(null)
    }
  }, [showArrivalGuide])

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
            <h1 className="text-4xl font-bold tracking-tight md:text-5xl">{isAgent ? 'Agent 中心' : '工具中心'}</h1>
            <p className="text-lg leading-8 text-muted-foreground">
              {isAgent
                ? '发现公司内部可直接使用的 AI Agent、机器人和自动化助手。'
                : '按工作场景发现在线工具、插件、MCP、内部服务和资源包。'}
            </p>
            <p className="text-sm text-muted-foreground">这里仅展示全公司可见，以及你所在部门可见的已发布内容。</p>
          </div>
          <Button
            size="lg"
            className={cn('shrink-0', tourTarget === 'publish' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target="publish"
            onClick={() => navigate({ to: '/dashboard/catalog/new', search: { kind: publishKind } })}
          >
            <Plus className="mr-2 h-4 w-4" />
            {publishLabel}
          </Button>
        </div>
        <form
          className={cn('mt-8 flex max-w-2xl gap-3 rounded-xl', tourTarget === 'search' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
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

      <div
        className={cn('flex flex-wrap items-center gap-2 rounded-xl', tourTarget === 'filters' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
        data-onboarding-target="filters"
      >
        {isAgent ? <>
          <span className="mr-1 text-sm font-medium text-muted-foreground">筛选</span>
          <select value={scenario} onChange={(event) => setScenario(event.target.value)} className="h-9 rounded-md border border-input bg-background px-3 text-sm">
            <option value="">全部场景</option>
            {scenario && !scenarios.includes(scenario) ? <option value={scenario}>{scenario}</option> : null}
            {scenarios.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
          <select value={departmentId?.toString() ?? ''} onChange={(event) => setDepartmentId(event.target.value ? Number(event.target.value) : undefined)} className="h-9 rounded-md border border-input bg-background px-3 text-sm">
            <option value="">全部部门</option>
            {departments.map((department) => <option key={department.id} value={department.id}>{department.displayName}</option>)}
          </select>
          <span className="ml-2 mr-1 text-sm font-medium text-muted-foreground">排序</span>
          <Button variant={sort === 'recommended' ? 'default' : 'outline'} size="sm" onClick={() => setSort('recommended')}>推荐</Button>
          <Button variant={sort === 'newest' ? 'default' : 'outline'} size="sm" onClick={() => setSort('newest')}>最新</Button>
        </> : <>
          <Button variant={kind === undefined ? 'default' : 'outline'} size="sm" onClick={() => setKind(undefined)}>全部</Button>
          {availableKinds.map((item) => (
            <Button key={item} variant={kind === item ? 'default' : 'outline'} size="sm" onClick={() => setKind(item)}>
              {catalogKindLabel(item)}
            </Button>
          ))}
        </>}
      </div>

      {isLoading ? <div className="py-20 text-center text-muted-foreground">正在加载...</div> : null}
      {isError ? <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-destructive">加载失败，请稍后重试。</div> : null}
      {!isLoading && !isError && resources.length === 0 ? (
        <div className="flex justify-center">
          <div className="w-full max-w-md rounded-2xl border border-dashed p-12 text-center text-muted-foreground">暂无匹配内容</div>
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
        {resources.map((resource, index) => (
          <div
            key={resource.id}
            className={cn('h-full rounded-2xl', isCatalogHighlighted && index === 0 && 'relative z-50 ring-4 ring-primary/50 ring-offset-4')}
            data-onboarding-target={isCatalogHighlighted && index === 0 ? 'catalog' : undefined}
          >
            <CatalogResourceCard
              resource={resource}
              onClick={() => navigate({ to: '/catalog/$slug', params: { slug: resource.slug } })}
              onUse={resource.kind === 'AGENT' && resource.accessUrl ? () => window.open(resource.accessUrl, '_blank', 'noopener,noreferrer') : undefined}
            />
          </div>
        ))}
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
