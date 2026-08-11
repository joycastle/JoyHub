import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { ArrowLeft, BookmarkCheck, BookmarkPlus, Building2, Copy, Download, ExternalLink, Heart, HeartOff, MessageCircle, MoreHorizontal, Pencil, Power, UserRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { resourcesApi } from '@/api/client'
import { catalogKindEmoji, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogResource } from '@/features/catalog/use-catalog-queries'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { ResourceDetailHeader, ResourceDetailLayout, ResourceDetailMetaCard } from '@/entities/resource/resource-detail-shell'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { cn } from '@/shared/lib/utils'
import { toast } from '@/shared/lib/toast'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import { useRecordResourceUse, useResourceLifecycleAction, useResourceStats, useToggleResourceFavorite } from '@/shared/hooks/use-resource-queries'
import { formatCompactCount } from '@/shared/lib/number-format'
import { useAuth } from '@/features/auth/use-auth'
import { useCommonTools } from '@/features/catalog/common-tools'

function copyPrompt(prompt: string) {
  void navigator.clipboard.writeText(prompt).then(
    () => toast.success('已复制', '粘贴到飞书机器人会话即可开始。'),
    () => toast.error('复制失败', '请手动复制这条示例提问。'),
  )
}

function statusClass(status?: string) {
  if (status === 'PUBLISHED') return 'badge-soft-green'
  if (status === 'ARCHIVED') return 'bg-secondary text-muted-foreground'
  return 'badge-soft-blue'
}

function statusLabel(status?: string) {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    OFFLINE: '已下线',
    ARCHIVED: '已归档',
  }[status ?? ''] ?? status
}

export function CatalogResourcePage() {
  const { t } = useTranslation()
  const { slug } = useParams({ from: '/catalog/$slug' })
  const navigate = useNavigate()
  const { data: resource, isLoading, isError } = useCatalogResource(slug)
  const { user } = useAuth()
  const resourceId = resource ? `catalog:${resource.id}` : ''
  const stats = useResourceStats(resourceId)
  const toggleFavorite = useToggleResourceFavorite()
  const recordUse = useRecordResourceUse()
  const { isCommonTool, recordToolUse, toggleTool } = useCommonTools()
  const publish = useResourceLifecycleAction('publish')
  const offline = useResourceLifecycleAction('offline')
  const archive = useResourceLifecycleAction('archive')
  const unarchive = useResourceLifecycleAction('unarchive')

  if (isLoading) return <div className="py-24 text-center text-muted-foreground">正在加载...</div>
  if (isError || !resource) return <div className="py-24 text-center text-destructive">内容不存在或无权访问。</div>

  const isAgent = resource.kind === 'AGENT'
  const isPublished = resource.status === 'PUBLISHED'
  const isArchived = resource.status === 'ARCHIVED'
  const canUpdateStaticVersion = resource.kind === 'ONLINE_TOOL' && resource.artifactAvailable
  const isFavorited = stats.data?.favorited ?? false

  const handleFavorite = async () => {
    if (!user) {
      navigate({ to: '/login', search: { returnTo: `/catalog/${encodeURIComponent(slug)}` } })
      return
    }
    try {
      const nextState = await toggleFavorite.mutateAsync({ resourceId, favorited: isFavorited })
      toast.success(nextState ? '已收藏' : '已取消收藏', nextState ? '已加入你的资源收藏。' : '已从资源收藏中移除。')
    } catch (error) {
      toast.error('收藏失败', error instanceof Error ? error.message : '请稍后重试。')
    }
  }

  const handleUse = () => {
    recordUse.mutate(resourceId, {
      onError: (error) => toast.error('使用次数统计失败', error instanceof Error ? error.message : '请稍后重试。'),
    })
    if (resource.kind !== 'AGENT') recordToolUse(resource.id)
  }

  const handleLifecycle = async () => {
    try {
      const updated = isPublished
        ? await offline.mutateAsync({ resourceId })
        : await publish.mutateAsync({ resourceId })
      toast.success(
        isPublished ? t('catalogDetail.offlineSuccessTitle') : t('catalogDetail.publishSuccessTitle'),
        t('catalogDetail.lifecycleSuccessDescription', { name: resource.name, status: updated.status }),
      )
    } catch (error) {
      toast.error(isPublished ? t('catalogDetail.offlineErrorTitle') : t('catalogDetail.publishErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleArchive = async () => {
    try {
      const updated = isArchived
        ? await unarchive.mutateAsync({ resourceId })
        : await archive.mutateAsync({ resourceId })
      toast.success(
        isArchived ? t('catalogDetail.unarchiveSuccessTitle') : t('catalogDetail.archiveSuccessTitle'),
        t('catalogDetail.lifecycleSuccessDescription', { name: resource.name, status: updated.status }),
      )
    } catch (error) {
      toast.error(
        isArchived ? t('catalogDetail.unarchiveErrorTitle') : t('catalogDetail.archiveErrorTitle'),
        error instanceof Error ? error.message : '',
      )
    }
  }

  const actionMenu = resource.canManage ? (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" aria-label={t('catalogDetail.moreActions')}>
          <MoreHorizontal className="mr-2 h-4 w-4" /> {t('catalogDetail.moreActions')}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => navigate({ to: '/dashboard/catalog/$slug/edit', params: { slug } })}>
          <Pencil className="mr-2 h-4 w-4" /> {canUpdateStaticVersion ? t('catalogDetail.updateVersion') : t('catalogDetail.edit')}
        </DropdownMenuItem>
        {!isArchived ? (
          <DropdownMenuItem onSelect={() => void handleLifecycle()}>
            <Power className="mr-2 h-4 w-4" /> {isPublished ? t('catalogDetail.offline') : t('catalogDetail.publish')}
          </DropdownMenuItem>
        ) : null}
        <DropdownMenuItem onSelect={() => void handleArchive()}>
          <Power className="mr-2 h-4 w-4" /> {isArchived ? t('catalogDetail.unarchive') : t('catalogDetail.archive')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate({ to: '/dashboard/resources' })}>
          {t('catalogDetail.backToMyContent')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  ) : null

  const backAction = (
    <Link to={isAgent ? '/agents' : '/tools'} className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="h-4 w-4" /> 返回{isAgent ? ' Agent 中心' : '工具中心'}
    </Link>
  )

  const badges = (
    <>
      <span className="badge-soft badge-soft-blue inline-flex items-center gap-2">
        <span aria-hidden="true">{resource.icon || catalogKindEmoji(resource.kind)}</span>
        {catalogKindLabel(resource.kind)}
      </span>
      {resource.status ? <span className={cn('badge-soft', statusClass(resource.status))}>{statusLabel(resource.status)}</span> : null}
      {resource.maintenanceStatus && resource.maintenanceStatus !== 'ACTIVE' ? (
        <span className="badge-soft bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400">{resource.maintenanceStatus}</span>
      ) : null}
      <span className="badge-soft inline-flex items-center gap-1">
        {resource.visibilityScope === 'COMPANY' ? '全公司可见' : '指定部门可见'}
      </span>
    </>
  )

  const tags = resource.tags?.map((tag) => (
    <span key={tag} className="inline-flex items-center rounded-full border border-border/60 bg-secondary/50 px-3 py-1 text-xs font-medium text-muted-foreground">
      {tag}
    </span>
  ))

  const sidebar = (
    <>
      <ResourceDetailMetaCard
        rows={[
          { label: '版本', value: resource.version ? `v${resource.version}` : '—' },
          { label: '归属部门', value: <span className="inline-flex items-center gap-1.5"><Building2 className="h-3.5 w-3.5" />{resource.department?.name || '全公司'}</span> },
          { label: '维护者', value: <span className="inline-flex items-center gap-1.5"><UserRound className="h-3.5 w-3.5" />{resource.owner?.displayName || resource.owner?.id || '—'}</span> },
          { label: '可见范围', value: resource.visibilityScope === 'COMPANY' ? '全公司' : '指定部门' },
          { label: '访问次数', value: formatCompactCount(stats.data?.viewCount ?? 0) },
          { label: '使用次数', value: formatCompactCount(stats.data?.useCount ?? 0) },
          { label: '下载次数', value: formatCompactCount(stats.data?.downloadCount ?? 0) },
          { label: '收藏数', value: formatCompactCount(stats.data?.favoriteCount ?? 0) },
        ]}
      />

      <Card className="sticky top-5 z-10 space-y-3 p-5 shadow-md">
        <div className="text-sm font-semibold font-heading text-foreground">使用与分发</div>
        {resource.accessUrl ? (
          <a href={resource.accessUrl} target="_blank" rel="noreferrer" onClick={handleUse} className={cn(buttonVariants({ size: 'lg' }), 'w-full gap-2')}>
            {isAgent ? '在飞书中使用' : '立即使用'} {isAgent ? <MessageCircle className="h-4 w-4" /> : <ExternalLink className="h-4 w-4" />}
          </a>
        ) : null}
        {resource.artifactAvailable ? (
          <a href={resourcesApi.downloadUrl(resourceId)} onClick={handleUse} className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'w-full gap-2')}>
            下载 {resource.artifactFilename || '安装包'} <Download className="h-4 w-4" />
          </a>
        ) : null}
        <Button
          variant="outline"
          size="lg"
          className="w-full"
          onClick={() => void handleFavorite()}
          disabled={toggleFavorite.isPending}
          aria-pressed={isFavorited}
          aria-label={isFavorited ? '取消收藏' : '收藏'}
        >
          {isFavorited ? <HeartOff className="mr-2 h-4 w-4" /> : <Heart className="mr-2 h-4 w-4" />}
          {isFavorited ? '取消收藏' : '收藏'}
        </Button>
        {!isAgent ? <Button variant="outline" size="lg" className="w-full" onClick={() => toggleTool(resource.id)} aria-pressed={isCommonTool(resource.id)}>
          {isCommonTool(resource.id) ? <BookmarkCheck className="mr-2 h-4 w-4" /> : <BookmarkPlus className="mr-2 h-4 w-4" />}
          {isCommonTool(resource.id) ? '移出常用工具' : '添加到常用工具'}
        </Button> : null}
      </Card>

      {resource.scenarios?.length ? (
        <Card>
          <CardHeader><CardTitle className="text-base">适用场景</CardTitle></CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {resource.scenarios.map((item) => <span key={item} className="rounded-full bg-secondary px-3 py-1 text-xs">{item}</span>)}
          </CardContent>
        </Card>
      ) : null}

      {resource.canManage ? (
        <Card className="space-y-3 p-5">
          <div className="flex items-center gap-2 text-sm font-semibold font-heading text-foreground"><Power className="h-4 w-4 text-muted-foreground" />资源状态</div>
          <p className="text-sm leading-6 text-muted-foreground">
            {isArchived ? '资源已归档，恢复后可继续维护。' : isPublished ? '资源当前对组织用户可用。' : '资源当前未发布，可从右上角操作菜单发布。'}
          </p>
        </Card>
      ) : null}
    </>
  )

  return (
    <ResourceDetailLayout sidebar={sidebar}>
      <ResourceDetailHeader
        backAction={backAction}
        badges={badges}
        title={resource.name}
        summary={resource.summary}
        owner={resource.owner?.displayName || resource.owner?.id}
        actions={actionMenu}
        tags={tags}
      />

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="usage">使用说明</TabsTrigger>
          <TabsTrigger value="related">关联能力</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-6">
          <Card className="space-y-6 p-8">
            <div>
              <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">资源简介</div>
              <p className="mt-3 text-base leading-7 text-foreground">{resource.summary || '维护者暂未补充简介。'}</p>
            </div>
            {resource.documentation ? <div className="border-t border-border/50 pt-6"><MarkdownRenderer content={resource.documentation} /></div> : null}
          </Card>
        </TabsContent>

        <TabsContent value="usage" className="mt-6 space-y-6">
          {isAgent ? (
            <Card>
              <CardHeader><CardTitle>快速开始</CardTitle></CardHeader>
              <CardContent className="space-y-6">
                {resource.agentExamplePrompts?.length ? (
                  <div>
                    <h2 className="font-semibold">你可以这样问</h2>
                    <div className="mt-3 grid gap-3">
                      {resource.agentExamplePrompts.map((prompt) => (
                        <div key={prompt} className="flex items-start justify-between gap-3 rounded-xl border bg-secondary/20 p-4 text-sm">
                          <p>{prompt}</p>
                          <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => copyPrompt(prompt)}><Copy className="mr-1.5 h-3.5 w-3.5" />复制</Button>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : <p className="text-sm text-muted-foreground">维护者暂未提供示例提问。可先打开飞书机器人，描述你的任务和期望结果。</p>}
                {resource.agentInputGuide || resource.agentOutputGuide || resource.agentUsageBoundary ? (
                  <div className="grid gap-4 border-t border-border/50 pt-6 md:grid-cols-2">
                    {resource.agentUsageBoundary ? <div><h3 className="font-semibold">适用边界</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{resource.agentUsageBoundary}</p></div> : null}
                    {resource.agentInputGuide ? <div><h3 className="font-semibold">输入说明</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{resource.agentInputGuide}</p></div> : null}
                    {resource.agentOutputGuide ? <div><h3 className="font-semibold">输出说明</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{resource.agentOutputGuide}</p></div> : null}
                    {resource.agentSupportContact ? <div><h3 className="font-semibold">支持联系</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{resource.agentSupportContact}</p></div> : null}
                  </div>
                ) : null}
              </CardContent>
            </Card>
          ) : null}
          <Card className="p-8">
            <CardHeader className="px-0 pt-0"><CardTitle>{isAgent ? '完整使用说明' : '使用说明'}</CardTitle></CardHeader>
            <CardContent className="px-0 pb-0">
              {resource.documentation ? <MarkdownRenderer content={resource.documentation} /> : <p className="text-muted-foreground">维护者暂未补充文档。</p>}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="related" className="mt-6 space-y-6">
          {resource.relatedResources?.length ? (
            <Card>
              <CardHeader><CardTitle>相关能力</CardTitle></CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-2">
                {resource.relatedResources.map((item) => <Link key={item.id} to="/catalog/$slug" params={{ slug: item.slug }} className="rounded-xl border p-4 text-sm font-medium text-primary transition-colors hover:bg-secondary/40">{item.name}<span className="mt-1 block text-xs font-normal text-muted-foreground">{catalogKindLabel(item.kind)}</span></Link>)}
              </CardContent>
            </Card>
          ) : null}
          {resource.relatedSkills?.length ? (
            <Card>
              <CardHeader><CardTitle>关联 Skill</CardTitle></CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-2">
                {resource.relatedSkills.map((item) => {
                  const content = <><p className="font-medium text-primary">{item.name}</p><p className="mt-1 text-xs text-muted-foreground">@{item.namespace}/{item.slug}{item.summary ? ` · ${item.summary}` : ''}</p></>
                  return item.namespace && item.slug
                    ? <Link key={item.id} to="/space/$namespace/$slug" params={{ namespace: item.namespace, slug: item.slug }} className="rounded-xl border p-4 text-sm transition-colors hover:bg-secondary/40">{content}</Link>
                    : <div key={item.id} className="rounded-xl border p-4 text-sm">{content}</div>
                })}
              </CardContent>
            </Card>
          ) : null}
          {!resource.relatedResources?.length && !resource.relatedSkills?.length ? <Card className="p-8 text-center text-muted-foreground">暂无关联能力。</Card> : null}
        </TabsContent>
      </Tabs>
    </ResourceDetailLayout>
  )
}
