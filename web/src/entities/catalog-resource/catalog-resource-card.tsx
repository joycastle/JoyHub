import { ArrowUpRight, Bookmark, BookmarkCheck, BookmarkPlus, Building2, Download, MessageCircle } from 'lucide-react'
import type { CatalogResourceSummary } from '@/api/types'
import { Card, CardContent } from '@/shared/ui/card'
import { catalogKindEmoji, catalogKindLabel } from './catalog-resource-kind'
import { useResourceStats } from '@/shared/hooks/use-resource-queries'

interface CatalogResourceCardProps {
  resource: CatalogResourceSummary
  onClick: () => void
  onUse?: () => void
  quickActionLabel?: string
  isCommonTool?: boolean
  onToggleCommonTool?: () => void
  variant?: 'default' | 'list'
}

export function CatalogResourceCard({ resource, onClick, onUse, quickActionLabel, isCommonTool, onToggleCommonTool, variant = 'default' }: CatalogResourceCardProps) {
  const { data: stats } = useResourceStats(`catalog:${resource.id}`, variant === 'list', false)
  if (variant === 'list') {
    return <Card role="button" tabIndex={0} className="group h-full cursor-pointer rounded-md border-border shadow-none transition hover:border-primary/50 hover:shadow-sm" onClick={onClick} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') onClick() }}>
      <CardContent className="flex min-h-40 flex-col p-4">
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-xl">{resource.icon || catalogKindEmoji(resource.kind)}</div>
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-base font-semibold leading-5 transition-colors group-hover:text-primary">{resource.name}</h3>
            <p className="mt-1 truncate text-xs text-muted-foreground">{resource.department?.name || resource.owner?.displayName || 'JoyHub 公共库'}</p>
          </div>
          <span className="shrink-0 rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground">{catalogKindLabel(resource.kind)}</span>
        </div>
        <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">{resource.summary}</p>
        <div className="mt-auto flex items-center justify-between gap-3 border-t border-border/60 pt-3 text-xs">
          <div className="flex items-center gap-3 text-muted-foreground">{stats?.downloadCount ? <span className="inline-flex items-center gap-1"><Download className="h-3.5 w-3.5" />{stats.downloadCount}</span> : null}{stats?.favoriteCount ? <span className="inline-flex items-center gap-1"><Bookmark className="h-3.5 w-3.5" />{stats.favoriteCount}</span> : null}</div>
          <div className="flex shrink-0 items-center gap-3 font-medium text-primary">{onUse ? <button type="button" onClick={(event) => { event.stopPropagation(); onUse() }} className="hover:underline">{quickActionLabel || '立即使用'}</button> : null}{onToggleCommonTool ? <button type="button" onClick={(event) => { event.stopPropagation(); onToggleCommonTool() }} className="inline-flex items-center gap-1 hover:underline" aria-pressed={isCommonTool}>{isCommonTool ? <BookmarkCheck className="h-3.5 w-3.5" /> : <BookmarkPlus className="h-3.5 w-3.5" />}{isCommonTool ? '已设为常用' : '添加到常用'}</button> : null}<span className="inline-flex items-center gap-1">详情 <ArrowUpRight className="h-3.5 w-3.5" /></span></div>
        </div>
      </CardContent>
    </Card>
  }
  return (
    <Card
      role="button"
      tabIndex={0}
      className="group h-full cursor-pointer overflow-hidden border-border/70"
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') onClick()
      }}
    >
      <CardContent className="flex h-full flex-col gap-5 p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-2xl">
              {resource.icon || catalogKindEmoji(resource.kind)}
            </div>
            <div>
              <div className="text-xs font-semibold uppercase tracking-wide text-primary">
                {catalogKindLabel(resource.kind)}
              </div>
              <h3 className="mt-1 text-lg font-semibold leading-tight text-foreground">{resource.name}</h3>
            </div>
          </div>
          <ArrowUpRight className="h-4 w-4 text-muted-foreground transition group-hover:text-primary" />
        </div>

        <p className="line-clamp-3 flex-1 text-sm leading-6 text-muted-foreground">{resource.summary}</p>

        <div className="flex flex-wrap gap-2">
          {(resource.scenarios ?? []).slice(0, 2).map((scenario) => (
            <span key={scenario} className="rounded-full bg-secondary px-2.5 py-1 text-xs text-secondary-foreground">
              {scenario}
            </span>
          ))}
          {resource.maintenanceStatus === 'MAINTENANCE' ? (
            <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs text-amber-800">维护中</span>
          ) : null}
        </div>

        <div className="flex items-center justify-between border-t border-border/60 pt-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <Building2 className="h-3.5 w-3.5" />
            {resource.department?.name || '全公司'}
          </span>
          <span>{resource.department?.name || resource.owner?.displayName || resource.owner?.id}</span>
          <span className="flex items-center gap-3 font-medium text-primary">
            {onUse ? <button type="button" onClick={(event) => { event.stopPropagation(); onUse() }} className="inline-flex items-center gap-1.5 hover:underline"><MessageCircle className="h-3.5 w-3.5" />{quickActionLabel || '立即使用'}</button> : null}
            {onToggleCommonTool ? <button type="button" onClick={(event) => { event.stopPropagation(); onToggleCommonTool() }} className="inline-flex items-center gap-1.5 hover:underline" aria-pressed={isCommonTool}>{isCommonTool ? <BookmarkCheck className="h-3.5 w-3.5" /> : <BookmarkPlus className="h-3.5 w-3.5" />}{isCommonTool ? '已设为常用' : '添加到常用'}</button> : null}
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
