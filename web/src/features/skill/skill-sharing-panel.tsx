import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Share2 } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { useSharingTargets } from '@/shared/hooks/use-sharing-targets'
import { useSkillSharing, useUpdateSkillSharing } from '@/shared/hooks/use-skill-sharing'

type SkillSharingPanelProps = {
  namespace: string
  slug: string
  canManage: boolean
}

/** Owner/admin-only editor for the departments explicitly granted access to a skill. */
export function SkillSharingPanel({ namespace, slug, canManage }: SkillSharingPanelProps) {
  const { t } = useTranslation()
  const { data: targets = [], isLoading: targetsLoading, error: targetsError } = useSharingTargets(canManage)
  const { data: sharing, isLoading: sharingLoading, error: sharingError } = useSkillSharing(namespace, slug, canManage)
  const updateSharing = useUpdateSkillSharing()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [restricted, setRestricted] = useState(false)

  useEffect(() => {
    setSelectedIds((sharing?.departments ?? []).map((target) => target.id))
    setRestricted(sharing?.restricted ?? false)
  }, [sharing?.departments, sharing?.restricted])

  if (!canManage) {
    return null
  }

  const isLoading = targetsLoading || sharingLoading
  const hasLoadError = Boolean(targetsError || sharingError)
  const isSaving = updateSharing.isPending
  const selected = new Set(selectedIds)

  const toggleTarget = (id: number) => {
    setSelectedIds((current) => current.includes(id)
      ? current.filter((currentId) => currentId !== id)
      : [...current, id])
  }

  const handleSave = () => {
    updateSharing.mutate(
      {
        namespace,
        slug,
        namespaceIds: selectedIds.slice().sort((left, right) => left - right),
        restricted,
      },
      {
        onSuccess: () => toast.success(t('skillDetail.sharingSaveSuccessTitle'), t('skillDetail.sharingSaveSuccessDescription')),
        onError: (error) => toast.error(
          t('skillDetail.sharingSaveErrorTitle'),
          error instanceof Error ? error.message : t('skillDetail.sharingSaveFallbackError'),
        ),
      },
    )
  }

  return (
    <Card className="p-5 space-y-4">
      <div className="space-y-1">
        <div className="flex items-center gap-2">
          <Share2 className="w-4 h-4 text-muted-foreground" />
          <span className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.sharingTitle')}</span>
        </div>
        <p className="text-sm text-muted-foreground">{t('skillDetail.sharingDescription')}</p>
      </div>

      {isLoading ? (
        <div className="rounded-xl border border-dashed border-border/70 px-3 py-4 text-sm text-muted-foreground">
          {t('skillDetail.sharingLoading')}
        </div>
      ) : hasLoadError ? (
        <div className="rounded-xl border border-dashed border-destructive/50 px-3 py-4 text-sm text-destructive">
          {t('skillDetail.sharingLoadError')}
        </div>
      ) : (
        <>
          <label className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/20 px-3 py-3">
            <input
              type="checkbox"
              checked={restricted}
              onChange={(event) => setRestricted(event.target.checked)}
              disabled={isSaving}
              className="mt-0.5 h-4 w-4 accent-primary"
            />
            <span className="text-sm text-foreground">
              {t('skillDetail.sharingRestrictLabel')}
              <span className="mt-1 block text-xs text-muted-foreground">{t('skillDetail.sharingRestrictHint')}</span>
            </span>
          </label>

          {restricted && targets.length > 0 ? (
            <div className="space-y-2">
              {targets.map((target) => (
                <label key={target.id} className="flex items-center gap-3 rounded-xl border border-border/60 px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selected.has(target.id)}
                    onChange={() => toggleTarget(target.id)}
                    disabled={isSaving}
                    className="h-4 w-4 accent-primary"
                  />
                  <span className="min-w-0 text-sm text-foreground">
                    {target.displayName}
                    <span className="ml-2 text-xs text-muted-foreground">@{target.slug}</span>
                  </span>
                </label>
              ))}
            </div>
          ) : restricted ? (
            <div className="rounded-xl border border-dashed border-border/70 px-3 py-4 text-sm text-muted-foreground">
              {t('skillDetail.sharingNoTargets')}
            </div>
          ) : null}
        </>
      )}

      <div className="flex justify-end">
        <Button type="button" size="sm" onClick={handleSave} disabled={isLoading || hasLoadError || isSaving}>
          {isSaving ? t('skillDetail.processing') : t('skillDetail.sharingSave')}
        </Button>
      </div>
    </Card>
  )
}
