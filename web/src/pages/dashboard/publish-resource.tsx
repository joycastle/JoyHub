import { useNavigate, useSearch } from '@tanstack/react-router'
import { CatalogResourceForm } from '@/features/catalog/catalog-resource-form'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { completeOnboardingTask } from '@/features/onboarding/onboarding-progress'
import { useAuth } from '@/features/auth/use-auth'

export function PublishResourcePage() {
  const navigate = useNavigate()
  const { kind, onboarding } = useSearch({ from: '/dashboard/catalog/new' })
  const { user } = useAuth()
  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div>
        <div className="text-sm font-semibold text-primary">JoyHub 发布中心</div>
        <h1 className="mt-2 text-4xl font-bold">{kind === 'AGENT' ? '发布飞书 Agent' : '发布 Agent 或工具'}</h1>
        <p className="mt-2 text-muted-foreground">{kind === 'AGENT' ? '发布后，员工可从 Agent 中心直接打开飞书机器人使用。' : '所有员工均可发布；发布者默认成为维护人。'}</p>
      </div>
      <CatalogResourceForm
        initialKind={kind}
        onboarding={onboarding}
        onOnboardingDismiss={() => navigate({ to: '/dashboard/catalog/new', search: { kind } })}
        onCreated={() => { completeOnboardingTask(user?.userId, 'publish'); navigate({ to: '/dashboard/resources', search: {} }) }}
      />
    </div>
  )
}
