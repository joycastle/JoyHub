import { ArrowUpRight, Building2 } from 'lucide-react'
import type { CatalogResourceSummary } from '@/api/types'
import { Card, CardContent } from '@/shared/ui/card'
import { catalogKindEmoji, catalogKindLabel } from './catalog-resource-kind'

interface CatalogResourceCardProps {
  resource: CatalogResourceSummary
  onClick: () => void
}

export function CatalogResourceCard({ resource, onClick }: CatalogResourceCardProps) {
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
          <span>{resource.owner?.displayName || resource.owner?.id}</span>
        </div>
      </CardContent>
    </Card>
  )
}
