import { Toaster as Sonner } from 'sonner'
import { CENTER_TOASTER_ID } from '@/shared/lib/toast'

export function Toaster() {
  return (
    <Sonner
      id={CENTER_TOASTER_ID}
      position="bottom-right"
      closeButton
      className="!bottom-4 !right-4 !left-auto"
      offset={16}
      mobileOffset={{ left: 16, right: 16, bottom: 16 }}
      toastOptions={{
        toasterId: CENTER_TOASTER_ID,
        classNames: {
          toast: 'glass-strong w-fit max-w-[min(100vw-2rem,32rem)] border border-border/40',
          title: 'text-foreground font-semibold text-center',
          description: 'text-muted-foreground text-center',
          content: 'w-full text-center',
          actionButton: 'bg-primary text-primary-foreground',
          cancelButton: 'bg-muted text-muted-foreground',
          error: 'border-destructive/40',
          success: 'border-emerald-500/40',
          warning: 'border-amber-500/40',
          info: 'border-blue-500/40',
        },
      }}
    />
  )
}
