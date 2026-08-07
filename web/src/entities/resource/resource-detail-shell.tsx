import type { ReactNode } from 'react'
import { User } from 'lucide-react'
import { Card } from '@/shared/ui/card'
import { cn } from '@/shared/lib/utils'

interface ResourceDetailLayoutProps {
  children: ReactNode
  sidebar?: ReactNode
}

/** Shared two-column frame used by every resource detail page. */
export function ResourceDetailLayout({ children, sidebar }: ResourceDetailLayoutProps) {
  if (!sidebar) {
    return <div className="mx-auto flex max-w-6xl flex-col gap-8 animate-fade-up lg:flex-row">{children}</div>
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8 animate-fade-up lg:flex-row">
      <main className="min-w-0 flex-1 space-y-8">{children}</main>
      {sidebar ? <aside className="w-full shrink-0 space-y-5 lg:w-80">{sidebar}</aside> : null}
    </div>
  )
}

interface ResourceDetailHeaderProps {
  backAction: ReactNode
  badges?: ReactNode
  title: string
  summary?: string
  owner?: string
  actions?: ReactNode
  tags?: ReactNode
}

/** Shared identity/header treatment; type-specific actions and metadata stay as slots. */
export function ResourceDetailHeader({
  backAction,
  badges,
  title,
  summary,
  owner,
  actions,
  tags,
}: ResourceDetailHeaderProps) {
  return (
    <header className="space-y-3">
      {backAction}
      {badges ? <div className="mb-1 flex flex-wrap items-center gap-3">{badges}</div> : null}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-balance font-heading text-4xl font-bold text-foreground">{title}</h1>
        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>
      {owner ? (
        <div className="flex min-w-0">
          <div className="inline-flex max-w-full items-center gap-2 rounded-full border border-border/60 bg-background/85 px-3 py-1.5 text-sm text-muted-foreground shadow-sm backdrop-blur-sm">
            <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-semibold uppercase tracking-[0.08em] text-primary">
              <User className="h-3.5 w-3.5" aria-hidden="true" />
            </span>
            <span className="min-w-0 truncate">{owner}</span>
          </div>
        </div>
      ) : null}
      {summary ? <p className="text-lg leading-relaxed text-muted-foreground">{summary}</p> : null}
      {tags ? <div className={cn('flex flex-wrap gap-2')}>{tags}</div> : null}
    </header>
  )
}

interface ResourceDetailMetaCardProps {
  rows: Array<{ label: string; value: ReactNode }>
  children?: ReactNode
}

/** Consistent metadata card for version, state, ownership, and social actions. */
export function ResourceDetailMetaCard({ rows, children }: ResourceDetailMetaCardProps) {
  return (
    <Card className="space-y-5 p-5">
      {rows.map((row, index) => (
        <div key={row.label}>
          {index > 0 ? <div className="mb-5 h-px bg-border/40" /> : null}
          <div className="flex items-start justify-between gap-4">
            <div className="text-sm text-muted-foreground">{row.label}</div>
            <div className="max-w-[11rem] break-words text-right font-semibold leading-snug text-foreground">{row.value}</div>
          </div>
        </div>
      ))}
      {children ? <><div className="h-px bg-border/40" />{children}</> : null}
    </Card>
  )
}
