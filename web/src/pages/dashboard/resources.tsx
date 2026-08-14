import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Download, Heart, HeartOff } from 'lucide-react'
import type { ResourceSummary } from '@/api/types'
import { catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { resourcesApi } from '@/api/client'
import { useResourceLifecycleAction, useToggleResourceFavorite } from '@/shared/hooks/use-resource-queries'
import { useMyResources } from '@/shared/hooks/use-user-queries'
import { buildReturnTo } from '@/shared/lib/auth-route'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { CenterFeatureTour, type CenterTourTarget } from '@/features/onboarding/center-feature-tour'
import { openOnboardingGuide } from '@/features/onboarding/onboarding-progress'

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
  const { onboarding } = useSearch({ from: '/dashboard/resources' })
  const location = useLocation()
  const [filter, setFilter] = useState<ResourceFilter>('ALL')
  const [keyword, setKeyword] = useState('')
  const [archiveTarget, setArchiveTarget] = useState<UnifiedResource | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<UnifiedResource | null>(null)
  const [highlightedTarget, setHighlightedTarget] = useState<CenterTourTarget | null>(null)

  const { data: resourcePage, isLoading } = useMyResources({ page: 0, size: 100 })
  const archiveResource = useResourceLifecycleAction('archive')
  const unarchiveResource = useResourceLifecycleAction('unarchive')
  const publishResource = useResourceLifecycleAction('publish')
  const offlineResource = useResourceLifecycleAction('offline')
  const toggleFavorite = useToggleResourceFavorite()

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
    if (!archiveTarget) return
    try {
      await archiveResource.mutateAsync({ resourceId: archiveTarget.resourceId })
      toast.success(t('resources.archiveSuccessTitle'), t('resources.archiveSuccessDescription', { name: archiveTarget.name }))
      setArchiveTarget(null)
    } catch (error) {
      toast.error(t('resources.archiveErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleUnarchive = async () => {
    if (!unarchiveTarget) return
    try {
      await unarchiveResource.mutateAsync({ resourceId: unarchiveTarget.resourceId })
      toast.success(t('resources.unarchiveSuccessTitle'), t('resources.unarchiveSuccessDescription', { name: unarchiveTarget.name }))
      setUnarchiveTarget(null)
    } catch (error) {
      toast.error(t('resources.unarchiveErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleCatalogLifecycle = (resource: UnifiedResource) => {
    if (resource.source === 'SKILL') return
    if (resource.status === 'ARCHIVED') {
      unarchiveResource.mutate({ resourceId: resource.resourceId })
    } else if (resource.status === 'PUBLISHED') {
      offlineResource.mutate({ resourceId: resource.resourceId })
    } else {
      publishResource.mutate({ resourceId: resource.resourceId })
    }
  }

  const handleArchiveResource = (resource: UnifiedResource) => {
    setArchiveTarget(resource)
  }

  const handleFavorite = (resource: UnifiedResource) => {
    toggleFavorite.mutate({ resourceId: resource.resourceId, favorited: resource.favorited })
  }

  const emptyAction = filter === 'SKILL'
    ? { label: t('resources.publishSkill'), onClick: () => navigate({ to: '/dashboard/publish', search: onboarding ? { onboarding: true } : {} }) }
    : { label: t('resources.publishResource'), onClick: () => navigate({ to: '/dashboard/catalog/new', search: onboarding ? { onboarding: true } : {} }) }

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} space-y-8`}>
      <DashboardPageHeader
        title={t('resources.title')}
        subtitle={t('resources.subtitle')}
        actions={(
          <div data-onboarding-target="publish" className={highlightedTarget === 'publish' ? 'relative z-50 flex flex-wrap gap-2 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4' : 'flex flex-wrap gap-2'}>
            <Button variant="outline" onClick={() => navigate({ to: '/dashboard/publish', search: onboarding ? { onboarding: true } : {} })}>{t('resources.publishSkill')}</Button>
            <Button onClick={() => navigate({ to: '/dashboard/catalog/new', search: onboarding ? { onboarding: true } : {} })}>{t('resources.publishResource')}</Button>
          </div>
        )}
      />

      <div data-onboarding-target="filters" className={highlightedTarget === 'filters' ? 'relative z-50 flex flex-col gap-3 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4 sm:flex-row' : 'flex flex-col gap-3 sm:flex-row'}>
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
        <div className="rounded-lg border border-dashed bg-white p-14 text-center text-muted-foreground">
          <p>{keyword.trim() ? t('resources.emptySearch') : t('resources.empty')}</p>
          <Button className="mt-5" onClick={emptyAction.onClick}>{emptyAction.label}</Button>
        </div>
      ) : null}

      <div data-onboarding-target="catalog" className={highlightedTarget === 'catalog' ? 'relative z-50 space-y-3 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4' : 'space-y-3'}>
        {resources.map((resource) => {
          const isArchivedSkill = resource.source === 'SKILL' && resource.status === 'ARCHIVED'
          const hasPublishedSkill = resource.source === 'SKILL' && resource.versionStatus === 'PUBLISHED'
          const canDownload = resource.actions.includes('DOWNLOAD')
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
                <div data-onboarding-target="manage" className={highlightedTarget === 'manage' ? 'relative z-50 flex shrink-0 flex-wrap items-center gap-2 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4' : 'flex shrink-0 flex-wrap items-center gap-2'} onClick={(event) => event.stopPropagation()}>
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
                      {resource.status === 'ARCHIVED' ? (
                        <Button size="sm" variant="outline" onClick={() => setUnarchiveTarget(resource)}>{t('resources.unarchive')}</Button>
                      ) : (
                        <>
                          <Button size="sm" variant={resource.status === 'PUBLISHED' ? 'outline' : 'default'} onClick={() => handleCatalogLifecycle(resource)}>
                            {resource.status === 'PUBLISHED' ? t('resources.offline') : t('resources.publish')}
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => handleArchiveResource(resource)}>{t('resources.archive')}</Button>
                        </>
                      )}
                    </>
                  )}
                  {canDownload ? <Button size="sm" variant="ghost" onClick={() => { window.location.href = resourcesApi.downloadUrl(resource.resourceId) }}><Download className="mr-1.5 h-4 w-4" />{t('resources.download')}</Button> : null}
                  <Button size="sm" variant="ghost" onClick={() => handleFavorite(resource)} aria-label={resource.favorited ? t('resources.removeFavorite') : t('resources.favorite')}>
                    {resource.favorited ? <HeartOff className="mr-1.5 h-4 w-4" /> : <Heart className="mr-1.5 h-4 w-4" />}
                    {resource.favorited ? t('resources.removeFavorite') : t('resources.favorite')}
                  </Button>
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
      {onboarding ? <CenterFeatureTour
        center="CONTENT"
        hasCatalogItems={resources.length > 0}
        onTargetChange={setHighlightedTarget}
        onDismiss={() => navigate({ to: '/dashboard/resources', search: {} })}
        onReturnToOnboarding={openOnboardingGuide}
      /> : null}
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
