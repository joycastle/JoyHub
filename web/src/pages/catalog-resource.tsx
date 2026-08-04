import { Link, useParams } from '@tanstack/react-router'
import { ArrowLeft, Building2, Download, ExternalLink, UserRound } from 'lucide-react'
import { catalogApi } from '@/api/client'
import { catalogKindEmoji, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResource } from '@/features/catalog/use-catalog-queries'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { cn } from '@/shared/lib/utils'

export function CatalogResourcePage() {
  const { slug } = useParams({ from: '/catalog/$slug' })
  const { data: resource, isLoading, isError } = useCatalogResource(slug)

  if (isLoading) return <div className="py-24 text-center text-muted-foreground">正在加载...</div>
  if (isError || !resource) return <div className="py-24 text-center text-destructive">内容不存在或无权访问。</div>

  return (
    <div className="space-y-8 animate-fade-up">
      <Link to={resource.kind === 'AGENT' ? '/agents' : '/tools'} className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 返回{resource.kind === 'AGENT' ? ' Agent 中心' : '工具中心'}
      </Link>

      <section className="rounded-3xl border bg-card p-7 md:p-10">
        <div className="flex flex-col justify-between gap-8 md:flex-row md:items-start">
          <div className="flex gap-5">
            <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-4xl">
              {resource.icon || catalogKindEmoji(resource.kind)}
            </div>
            <div>
              <div className="text-sm font-semibold text-primary">{catalogKindLabel(resource.kind)}</div>
              <h1 className="mt-1 text-4xl font-bold tracking-tight">{resource.name}</h1>
              <p className="mt-4 max-w-3xl text-lg leading-8 text-muted-foreground">{resource.summary}</p>
            </div>
          </div>
          <div className="flex shrink-0 flex-wrap gap-3">
            {resource.accessUrl ? (
              <a href={resource.accessUrl} target="_blank" rel="noreferrer" className={cn(buttonVariants({ size: 'lg' }), 'gap-2')}>
                立即使用 <ExternalLink className="h-4 w-4" />
              </a>
            ) : null}
            {resource.artifactAvailable ? (
              <a href={catalogApi.artifactUrl(resource.slug)} className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'gap-2')}>
                下载 {resource.artifactFilename || '安装包'} <Download className="h-4 w-4" />
              </a>
            ) : null}
          </div>
        </div>
        <div className="mt-8 flex flex-wrap gap-x-6 gap-y-3 border-t pt-6 text-sm text-muted-foreground">
          <span className="flex items-center gap-2"><Building2 className="h-4 w-4" />{resource.department?.name || '全公司'}</span>
          <span className="flex items-center gap-2"><UserRound className="h-4 w-4" />{resource.owner?.displayName || resource.owner?.id}</span>
          <span>{resource.visibilityScope === 'COMPANY' ? '全公司可见' : '指定部门可见'}</span>
          {resource.version ? <span>版本 {resource.version}</span> : null}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <Card>
          <CardHeader><CardTitle>使用说明</CardTitle></CardHeader>
          <CardContent>
            {resource.documentation ? <MarkdownRenderer content={resource.documentation} /> : <p className="text-muted-foreground">维护者暂未补充文档。</p>}
          </CardContent>
        </Card>
        <aside className="space-y-5">
          <Card>
            <CardHeader><CardTitle className="text-base">适用场景</CardTitle></CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              {(resource.scenarios ?? []).map((item) => <span key={item} className="rounded-full bg-secondary px-3 py-1 text-xs">{item}</span>)}
            </CardContent>
          </Card>
          {resource.relatedResources?.length ? (
            <Card>
              <CardHeader><CardTitle className="text-base">相关能力</CardTitle></CardHeader>
              <CardContent className="space-y-3">
                {resource.relatedResources.map((item) => <Link key={item.id} to="/catalog/$slug" params={{ slug: item.slug }} className="block text-sm font-medium text-primary hover:underline">{item.name}</Link>)}
              </CardContent>
            </Card>
          ) : null}
        </aside>
      </div>
    </div>
  )
}
