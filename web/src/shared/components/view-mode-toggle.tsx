import { Grid2X2, List } from 'lucide-react'
import { cn } from '@/shared/lib/utils'

export type ViewMode = 'list' | 'grid'

export function ViewModeToggle({ value, onChange, className }: { value: ViewMode; onChange: (value: ViewMode) => void; className?: string }) {
  return (
    <div className={cn('inline-flex rounded-md bg-slate-100 p-1', className)} role="group" aria-label="切换查看方式">
      <button type="button" onClick={() => onChange('list')} aria-label="列表视图" aria-pressed={value === 'list'} className={cn('inline-flex h-8 w-9 items-center justify-center rounded-md text-muted-foreground transition', value === 'list' && 'bg-white text-foreground shadow-sm')}><List className="h-4 w-4" /></button>
      <button type="button" onClick={() => onChange('grid')} aria-label="宫格视图" aria-pressed={value === 'grid'} className={cn('inline-flex h-8 w-9 items-center justify-center rounded-md text-muted-foreground transition', value === 'grid' && 'bg-white text-foreground shadow-sm')}><Grid2X2 className="h-4 w-4" /></button>
    </div>
  )
}
