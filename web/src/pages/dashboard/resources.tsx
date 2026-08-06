import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { ResourceSummary } from '@/api/types'
import { catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCatalogLifecycleAction } from '@/features/catalog/use-catalog-queries'
import { useArchiveSkill, useUnarchiveSkill } from '@/shared/hooks/use-skill-queries'
import { useMyResources } from '@/shared/hooks/use-user-queries'
import { buildReturnTo } from '@/shared/lib/auth-route'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type ResourceFilter = 'ALL' | 'SKILL' | 'CATALOG'

type UnifiedResource = ResourceSummary & {
  source: 'SKILL' | 'CATALOG'
}

function resourceKindLabel(resource: UnifiedResource): string {
  return resource.source === 'SKILL' ? 'Skill' : catalogKindLabel(resource.kind as Parameters<typeof catalogKindLabel>[0])
}

function resourceStatusLabel(resource: UnifiedResource, t: (key: string) => string): string {
  if (resource.source === 'SKILL') {
    const labels: Record<string, string> = {
      ACTIVE: t('resources.status.active'),
      ARCHIVED: t('resources.status.archived'),
      PENDING_REVIEW: t('resources.status.pendingReview'),
      PUBLISHED: t('resources.status.published'),
      REJECTED: t('resources.status.rejected'),
      DRAFT: t('resources.status.draft'),
    }
    return labels[resource.status] ?? resource.status
  }
  const labels: Record<string, string> = {
    DRAFT: t('resources.status.draft'),
    PUBLISHED: t('resources.status.published'),
    OFFLINE: t('resources.status.offline'),
  }
  return labels[resource.status] ?? resource.status
}

export function ResourcesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [filter, setFilter] = useState<ResourceFilter>('ALL')
  const [keyword, setKeyword] = useState('')
  const [archiveTarget, setArchiveTarget] = useState<UnifiedResource | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<UnifiedResource | null>(null)

  const { data: resourcePage, isLoading } = useMyResources({ page: 0, size: 100 })
  const archiveSkill = useArchiveSkill()
  const unarchiveSkill = useUnarchiveSkill()
  const catalogLifecycle = useCatalogLifecycleAction('publish')
  const catalogOffline = useCatalogLifecycleAction('offline')

  const resources = useMemo(() => {
    const combined: UnifiedResource[] = (resourcePage?.items ?? []).map((resource) => ({
      ...resource,
      source: resource.sourceType === 'SKILL' ? 'SKILL' : 'CATALOG',
    }))
    const normalizedKeyword = keyword.trim().toLowerCase()
    return combined
      .filter((resource) => filter === 'ALL' || resource.source === filter)
      .filter((resource) => {
        if (!normalizedKeyword) return true
        return [resource.name, resource.slug, resource.summary ?? '', resourceKindLabel(resource)]
          .some((value) => value.toLowerCase().includes(normalizedKeyword))
      })
      .sort((left, right) => (right.updatedAt ?? '').localeCompare(left.updatedAt ?? ''))
  }, [filter, keyword, resourcePage?.items])

  const openResource = (resource: UnifiedResource) => {
    if (resource.source === 'SKILL' && resource.namespace) {
      navigate({
        to: `/space/${resource.namespace}/${encodeURIComponent(resource.slug)}`,
        search: { returnTo: buildReturnTo(location) },
      })
      return
    }
    navigate({ to: '/catalog/$slug', params: { slug: resource.slug } })
  }

  const updateSkill = (resource: UnifiedResource) => {
    if (resource.source !== 'SKILL' || !resource.namespace) return
    navigate({
      to: '/dashboard/publish',
      search: { namespace: resource.namespace, visibility: resource.visibility ?? 'WAREHOUSE' },
    })
  }

  const handleArchive = async () => {
    if (!archiveTarget || archiveTarget.source !== 'SKILL' || !archiveTarget.namespace) return
    try {
      await archiveSkill.mutateAsync({ namespace: archiveTarget.namespace, slug: archiveTarget.slug })
      toast.success(t('resources.archiveSuccessTitle'), t('resources.archiveSuccessDescription', { name: archiveTarget.name }))
      setArchiveTarget(null)
    } catch (error) {
      toast.error(t('resources.archiveErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleUnarchive = async () => {
    if (!unarchiveTarget || unarchiveTarget.source !== 'SKILL' || !unarchiveTarget.namespace) return
    try {
      await unarchiveSkill.mutateAsync({ namespace: unarchiveTarget.namespace, slug: unarchiveTarget.slug })
      toast.success(t('resources.unarchiveSuccessTitle'), t('resources.unarchiveSuccessDescription', { name: unarchiveTarget.name }))
      setUnarchiveTarget(null)
    } catch (error) {
      toast.error(t('resources.unarchiveErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleCatalogLifecycle = (resource: UnifiedResource) => {
    if (resource.source === 'SKILL') return
    if (resource.status === 'PUBLISHED') {
      catalogOffline.mutate(resource.slug)
    } else {
      catalogLifecycle.mutate(resource.slug)
    }
  }

  const emptyAction = filter === 'SKILL'
    ? { label: t('resources.publishSkill'), onClick: () => navigate({ to: '/dashboard/publish' }) }
    : { label: t('resources.publishResource'), onClick: () => navigate({ to: '/dashboard/catalog/new' }) }

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} space-y-8`}>
      <DashboardPageHeader
        title={t('resources.title')}
        subtitle={t('resources.subtitle')}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => navigate({ to: '/dashboard/publish' })}>{t('resources.publishSkill')}</Button>
            <Button onClick={() => navigate({ to: '/dashboard/catalog/new' })}>{t('resources.publishResource')}</Button>
          </div>
        )}
      />

      <div className="flex flex-col gap-3 sm:flex-row">
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder={t('resources.searchPlaceholder')}
          aria-label={t('resources.searchPlaceholder')}
          className="sm:max-w-md"
        />
        <div className="flex flex-wrap gap-2">
          {(['ALL', 'SKILL', 'CATALOG'] as ResourceFilter[]).map((option) => (
            <Button key={option} size="sm" variant={filter === option ? 'default' : 'outline'} onClick={() => setFilter(option)}>
              {t(`resources.filters.${option}`)}
            </Button>
          ))}
        </div>
      </div>

      {isLoading ? <div className="py-16 text-center text-muted-foreground">{t('resources.loading')}</div> : null}

      {!isLoading && resources.length === 0 ? (
        <div className="rounded-2xl border border-dashed p-14 text-center text-muted-foreground">
          <p>{keyword.trim() ? t('resources.emptySearch') : t('resources.empty')}</p>
          <Button className="mt-5" onClick={emptyAction.onClick}>{emptyAction.label}</Button>
        </div>
      ) : null}

      <div className="space-y-3">
        {resources.map((resource) => {
          const isArchivedSkill = resource.source === 'SKILL' && resource.status === 'ARCHIVED'
          const hasPublishedSkill = resource.source === 'SKILL' && resource.versionStatus === 'PUBLISHED'
          return (
            <Card key={resource.resourceId} className="cursor-pointer transition-colors hover:border-primary/50" onClick={() => openResource(resource)}>
              <CardContent className="flex flex-col justify-between gap-4 p-5 md:flex-row md:items-center">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2 text-xs font-semibold text-primary">
                    <span>{resourceKindLabel(resource)}</span>
                    <span className="rounded-full bg-secondary px-2 py-0.5 font-normal text-muted-foreground">{resourceStatusLabel(resource, t)}</span>
                    {resource.version ? <span className="font-mono font-normal text-muted-foreground">v{resource.version}</span> : null}
                  </div>
                  <h2 className="mt-2 text-lg font-semibold hover:text-primary">{resource.name}</h2>
                  <p className="mt-1 line-clamp-1 text-sm text-muted-foreground">{resource.summary || t('resources.noSummary')}</p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2" onClick={(event) => event.stopPropagation()}>
                  {resource.source === 'SKILL' ? (
                    <>
                      {!isArchivedSkill ? <Button size="sm" variant="outline" onClick={() => updateSkill(resource)}>{t('resources.updateVersion')}</Button> : null}
                      {isArchivedSkill ? (
                        <Button size="sm" variant="outline" onClick={() => setUnarchiveTarget(resource)}>{t('resources.unarchive')}</Button>
                      ) : hasPublishedSkill ? (
                        <Button size="sm" variant="outline" onClick={() => setArchiveTarget(resource)}>{t('resources.archive')}</Button>
                      ) : null}
                    </>
                  ) : (
                    <>
                      <Link to="/dashboard/catalog/$slug/edit" params={{ slug: resource.slug }}>
                        <Button size="sm" variant="outline">{t('resources.edit')}</Button>
                      </Link>
                      <Button size="sm" variant={resource.status === 'PUBLISHED' ? 'outline' : 'default'} onClick={() => handleCatalogLifecycle(resource)}>
                        {resource.status === 'PUBLISHED' ? t('resources.offline') : t('resources.publish')}
                      </Button>
                    </>
                  )}
                  <Button size="sm" variant="ghost" onClick={() => openResource(resource)}>{t('resources.open')}</Button>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      <ConfirmDialog
        open={!!archiveTarget}
        onOpenChange={(open) => { if (!open) setArchiveTarget(null) }}
        title={t('resources.archiveConfirmTitle')}
        description={archiveTarget ? t('resources.archiveConfirmDescription', { name: archiveTarget.name }) : ''}
        confirmText={t('resources.archive')}
        onConfirm={handleArchive}
      />
      <ConfirmDialog
        open={!!unarchiveTarget}
        onOpenChange={(open) => { if (!open) setUnarchiveTarget(null) }}
        title={t('resources.unarchiveConfirmTitle')}
        description={unarchiveTarget ? t('resources.unarchiveConfirmDescription', { name: unarchiveTarget.name }) : ''}
        confirmText={t('resources.unarchive')}
        onConfirm={handleUnarchive}
      />
    </div>
  )
}
