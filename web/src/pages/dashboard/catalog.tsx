import { Link } from '@tanstack/react-router'
import { catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogLifecycleAction, useMyCatalogResources } from '@/features/catalog/use-catalog-queries'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

export function MyCatalogPage() {
  const { data, isLoading } = useMyCatalogResources()
  const publish = useCatalogLifecycleAction('publish')
  const offline = useCatalogLifecycleAction('offline')

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><h1 className="text-4xl font-bold">我维护的能力</h1><p className="mt-2 text-muted-foreground">管理 Agent、插件、工具和服务的发布状态。</p></div>
        <Link to="/dashboard/catalog/new" className="rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground">发布新内容</Link>
      </div>
      {isLoading ? <div className="py-16 text-center text-muted-foreground">正在加载...</div> : null}
      <div className="space-y-3">
        {data?.items.map((item) => (
          <Card key={item.id}>
            <CardContent className="flex flex-col justify-between gap-4 p-5 md:flex-row md:items-center">
              <div>
                <div className="flex items-center gap-2"><span className="text-xs font-semibold text-primary">{catalogKindLabel(item.kind)}</span><span className="rounded-full bg-secondary px-2 py-0.5 text-xs">{item.status}</span></div>
                <Link to="/catalog/$slug" params={{ slug: item.slug }} className="mt-2 block text-lg font-semibold hover:text-primary">{item.name}</Link>
                <p className="mt-1 line-clamp-1 text-sm text-muted-foreground">{item.summary}</p>
              </div>
              <div className="flex gap-2">
                {item.status !== 'PUBLISHED' ? <Button size="sm" onClick={() => publish.mutate(item.slug)}>发布</Button> : <Button size="sm" variant="outline" onClick={() => offline.mutate(item.slug)}>下架</Button>}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
      {!isLoading && data?.items.length === 0 ? <div className="rounded-2xl border border-dashed p-14 text-center text-muted-foreground">你还没有发布内容。</div> : null}
    </div>
  )
}
