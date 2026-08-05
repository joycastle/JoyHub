import { Link, useParams } from '@tanstack/react-router'
import { ArrowLeft, Building2, Copy, Download, ExternalLink, MessageCircle, UserRound } from 'lucide-react'
import { catalogApi } from '@/api/client'
import { catalogKindEmoji, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResource } from '@/features/catalog/use-catalog-queries'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { cn } from '@/shared/lib/utils'
import { toast } from '@/shared/lib/toast'

function copyPrompt(prompt: string) {
  void navigator.clipboard.writeText(prompt).then(
    () => toast.success('已复制', '粘贴到飞书机器人会话即可开始。'),
    () => toast.error('复制失败', '请手动复制这条示例提问。'),
  )
}

export function CatalogResourcePage() {
  const { slug } = useParams({ from: '/catalog/$slug' })
  const { data: resource, isLoading, isError } = useCatalogResource(slug)

  if (isLoading) return <div className="py-24 text-center text-muted-foreground">正在加载...</div>
  if (isError || !resource) return <div className="py-24 text-center text-destructive">内容不存在或无权访问。</div>
  const isAgent = resource.kind === 'AGENT'

  return (
    <div className="space-y-8 animate-fade-up">
      <Link to={isAgent ? '/agents' : '/tools'} className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 返回{isAgent ? ' Agent 中心' : '工具中心'}
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
              <a href={resource.accessUrl} target="_blank" rel="noreferrer" className={cn(buttonVariants(), 'gap-2')}>
                {isAgent ? '在飞书中使用' : '立即使用'} {isAgent ? <MessageCircle className="h-4 w-4" /> : <ExternalLink className="h-4 w-4" />}
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

      {isAgent ? <section>
        <Card>
          <CardHeader><CardTitle>快速开始</CardTitle></CardHeader>
          <CardContent className="space-y-6">
            {resource.agentExamplePrompts?.length ? <div>
              <h2 className="font-semibold">你可以这样问</h2>
              <div className="mt-3 grid gap-3">
                {resource.agentExamplePrompts.map((prompt) => <div key={prompt} className="flex items-start justify-between gap-3 rounded-xl border bg-secondary/20 p-4 text-sm"><p>{prompt}</p><Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => copyPrompt(prompt)}><Copy className="mr-1.5 h-3.5 w-3.5" />复制</Button></div>)}
              </div>
            </div> : <p className="text-sm text-muted-foreground">维护者暂未提供示例提问。可先打开飞书机器人，描述你的任务和期望结果。</p>}
          </CardContent>
        </Card>
      </section> : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <Card>
          <CardHeader><CardTitle>{isAgent ? '完整使用说明' : '使用说明'}</CardTitle></CardHeader>
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
          {resource.relatedSkills?.length ? (
            <Card>
              <CardHeader><CardTitle className="text-base">关联 Skill</CardTitle></CardHeader>
              <CardContent className="space-y-3">
                {resource.relatedSkills.map((item) => <div key={item.id} className="text-sm"><p className="font-medium">{item.name}</p><p className="mt-1 text-xs text-muted-foreground">@{item.namespace}/{item.slug}{item.summary ? ` · ${item.summary}` : ''}</p></div>)}
              </CardContent>
            </Card>
          ) : null}
        </aside>
      </div>
    </div>
  )
}
