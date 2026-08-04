import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'
import type { CatalogCenter, CatalogResourceKind } from '@/api/types'
import { CatalogResourceCard } from '@/entities/catalog-resource/catalog-resource-card'
import { CATALOG_RESOURCE_KINDS, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResources } from '@/features/catalog/use-catalog-queries'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

function CatalogCenterPage({ center }: { center: CatalogCenter }) {
  const navigate = useNavigate()
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [kind, setKind] = useState<CatalogResourceKind | undefined>()
  const { data, isLoading, isError } = useCatalogResources({ center, q: query, kind, size: 48 })
  const isAgent = center === 'AGENT'
  const availableKinds = isAgent ? ['AGENT'] as CatalogResourceKind[] : CATALOG_RESOURCE_KINDS.filter((item) => item !== 'AGENT')
  const publishKind: CatalogResourceKind = isAgent ? 'AGENT' : (kind ?? 'ONLINE_TOOL')
  const publishLabel = isAgent ? '发布 Agent' : `发布${kind ? catalogKindLabel(kind) : '工具'}`

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
            className="shrink-0"
            onClick={() => navigate({ to: '/dashboard/catalog/new', search: { kind: publishKind } })}
          >
            <Plus className="mr-2 h-4 w-4" />
            {publishLabel}
          </Button>
        </div>
        <form
          className="mt-8 flex max-w-2xl gap-3"
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

      {!isAgent ? (
        <div className="flex flex-wrap gap-2">
          <Button variant={kind === undefined ? 'default' : 'outline'} size="sm" onClick={() => setKind(undefined)}>全部</Button>
          {availableKinds.map((item) => (
            <Button key={item} variant={kind === item ? 'default' : 'outline'} size="sm" onClick={() => setKind(item)}>
              {catalogKindLabel(item)}
            </Button>
          ))}
        </div>
      ) : null}

      {isLoading ? <div className="py-20 text-center text-muted-foreground">正在加载...</div> : null}
      {isError ? <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-destructive">加载失败，请稍后重试。</div> : null}
      {!isLoading && !isError && data?.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed p-16 text-center text-muted-foreground">暂无匹配内容</div>
      ) : null}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
        {data?.items.map((resource) => (
          <CatalogResourceCard
            key={resource.id}
            resource={resource}
            onClick={() => navigate({ to: '/catalog/$slug', params: { slug: resource.slug } })}
          />
        ))}
      </div>
    </div>
  )
}

export function AgentsPage() {
  return <CatalogCenterPage center="AGENT" />
}

export function ToolsPage() {
  return <CatalogCenterPage center="TOOL" />
}
