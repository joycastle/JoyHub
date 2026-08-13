import type { ReactNode } from 'react'
import { Plus } from 'lucide-react'
import type { ViewMode } from '@/shared/components/view-mode-toggle'
import { ViewModeToggle } from '@/shared/components/view-mode-toggle'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import { SearchBar } from './search-bar'

interface ResourceCenterShellProps {
  eyebrow: string
  title: string
  description: string
  visibility: string
  publishLabel: string
  onPublish: () => void
  queryInput: string
  onQueryChange: (value: string) => void
  onSearch: (value: string) => void
  searchPlaceholder: string
  isSearching?: boolean
  filters: ReactNode
  resultCount?: number
  resultCountLabel: string
  viewMode: ViewMode
  onViewModeChange: (value: ViewMode) => void
  highlightedTarget?: 'publish' | 'search' | 'filters' | null
  children: ReactNode
  className?: string
}

/** Consistent discovery frame for the Skill, Agent, and Tool centers. */
export function ResourceCenterShell({
  eyebrow,
  title,
  description,
  visibility,
  publishLabel,
  onPublish,
  queryInput,
  onQueryChange,
  onSearch,
  searchPlaceholder,
  isSearching = false,
  filters,
  resultCount,
  resultCountLabel,
  viewMode,
  onViewModeChange,
  highlightedTarget,
  children,
  className,
}: ResourceCenterShellProps) {
  return (
    <div className={cn('mx-auto w-full max-w-[1200px] space-y-7', className)}>
      <section className="border-b border-border pb-7">
        <div className="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
          <div className="max-w-3xl">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{eyebrow}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight md:text-4xl">{title}</h1>
            <p className="mt-3 text-base leading-7 text-muted-foreground">{description}</p>
            <p className="mt-1 text-sm text-muted-foreground">{visibility}</p>
          </div>
          <Button
            size="lg"
            className={cn(
              'shrink-0 rounded-md shadow-none',
              highlightedTarget === 'publish' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4',
            )}
            data-onboarding-target="publish"
            onClick={onPublish}
          >
            <Plus className="mr-2 h-4 w-4" />
            {publishLabel}
          </Button>
        </div>

        <div
          className={cn(
            'mt-6 max-w-3xl rounded-lg',
            highlightedTarget === 'search' && 'relative z-50 ring-4 ring-primary/50 ring-offset-4',
          )}
          data-onboarding-target="search"
        >
          <SearchBar
            variant="compact"
            value={queryInput}
            placeholder={searchPlaceholder}
            isSearching={isSearching}
            onChange={onQueryChange}
            onSearch={onSearch}
          />
        </div>
      </section>

      <section>
        <div
          className={cn(
            'flex flex-col gap-3 border-b border-border pb-4 lg:flex-row lg:items-center',
            highlightedTarget === 'filters' && 'relative z-50 rounded-lg bg-background ring-4 ring-primary/50 ring-offset-4',
          )}
          data-onboarding-target="filters"
        >
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">{filters}</div>
          <div className="flex shrink-0 items-center justify-between gap-3 lg:justify-end">
            {resultCount !== undefined ? (
              <span className="text-sm text-muted-foreground">{resultCountLabel}</span>
            ) : null}
            <ViewModeToggle value={viewMode} onChange={onViewModeChange} />
          </div>
        </div>
        <div className="mt-6">{children}</div>
      </section>
    </div>
  )
}
