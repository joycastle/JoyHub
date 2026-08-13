import type { ReactNode } from 'react'
import type { ViewMode } from './view-mode-toggle'
import { cn } from '@/shared/lib/utils'

export function ResourceResultGrid({ viewMode, children, className }: { viewMode: ViewMode; children: ReactNode; className?: string }) {
  return (
    <div className={cn(
      'grid gap-4',
      viewMode === 'list' ? 'grid-cols-1 xl:grid-cols-2' : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
      className,
    )}>
      {children}
    </div>
  )
}
