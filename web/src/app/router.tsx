import { lazy, Suspense, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'
import { RoleGuard } from '@/shared/components/role-guard'
import { createRequireAuth } from '@/shared/lib/auth-route'
import { isApiTokensEnabled } from '@/shared/config/features'
import { clearDynamicImportReloadGuard, recoverFromDynamicImportError } from '@/shared/lib/dynamic-import-recovery'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import type { CatalogResourceKind } from '@/api/types'
import { CATALOG_RESOURCE_KINDS } from '@/entities/catalog-resource/catalog-resource-kind'

/**
 * Central route registry for the SkillHub web app.
 *
 * This file keeps route declarations, auth redirects, role-based wrappers, and search-param
 * normalization in one place so route behavior remains explicit.
 */
// Capture original URL before TanStack Router rewrites it
const ORIGINAL_URL_SEARCH = typeof window !== 'undefined' ? window.location.search : ''

// Export for use in cli-auth page
export { ORIGINAL_URL_SEARCH }

function createLazyRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  // Lazy route modules are wrapped in a uniform suspense fallback so route transitions behave
  // consistently across public and dashboard pages.
  const LazyComponent = lazy(async () => {
    const module = await importer().catch((error) => {
      if (recoverFromDynamicImportError(error)) {
        return new Promise<never>(() => {})
      }
      throw error
    })
    // Router resolution can finish before React.lazy imports the route module. Only clear the
    // one-time reload guard after the chunk itself has loaded successfully.
    clearDynamicImportReloadGuard()
    return { default: module[exportName] as ComponentType<Record<string, unknown>> }
  })

  return function LazyRouteComponent(props: Record<string, unknown>) {
    return (
      <Suspense
        fallback={
          <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
            Loading...
          </div>
        }
      >
        <LazyComponent {...props} />
      </Suspense>
    )
  }
}

function createRoleProtectedRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
  allowedRoles: readonly string[],
) {
  // Role checks stay at the route edge so page modules can assume the minimum permission level.
  const RouteComponent = createLazyRouteComponent(importer, exportName)

  return function RoleProtectedRouteComponent(props: Record<string, unknown>) {
    return (
      <RoleGuard allowedRoles={allowedRoles}>
        <RouteComponent {...props} />
      </RoleGuard>
    )
  }
}

const LandingPage = createLazyRouteComponent(() => import('@/pages/landing'), 'LandingPage')
const HomePage = createLazyRouteComponent(() => import('@/pages/home'), 'HomePage')
const AgentsPage = createLazyRouteComponent(() => import('@/pages/catalog-center'), 'AgentsPage')
const ToolsPage = createLazyRouteComponent(() => import('@/pages/catalog-center'), 'ToolsPage')
const CatalogResourcePage = createLazyRouteComponent(() => import('@/pages/catalog-resource'), 'CatalogResourcePage')
const LoginPage = createLazyRouteComponent(() => import('@/pages/login'), 'LoginPage')
const PrivacyPolicyPage = createLazyRouteComponent(() => import('@/pages/privacy'), 'PrivacyPolicyPage')
const SearchPage = createLazyRouteComponent(() => import('@/pages/search'), 'SearchPage')
const TermsOfServicePage = createLazyRouteComponent(() => import('@/pages/terms'), 'TermsOfServicePage')
const NamespacePage = createLazyRouteComponent(() => import('@/pages/namespace'), 'NamespacePage')
const SkillDetailPage = createLazyRouteComponent(() => import('@/pages/skill-detail'), 'SkillDetailPage')
const SkillVersionComparePage = createLazyRouteComponent(() => import('@/pages/skill-version-compare'), 'SkillVersionComparePage')
const DashboardPage = createLazyRouteComponent(() => import('@/pages/dashboard'), 'DashboardPage')
const ResourcesPage = createLazyRouteComponent(() => import('@/pages/dashboard/resources'), 'ResourcesPage')
const PublishPage = createLazyRouteComponent(() => import('@/pages/dashboard/publish'), 'PublishPage')
const PublishResourcePage = createLazyRouteComponent(() => import('@/pages/dashboard/publish-resource'), 'PublishResourcePage')
const EditCatalogResourcePage = createLazyRouteComponent(() => import('@/pages/dashboard/edit-catalog-resource'), 'EditCatalogResourcePage')
const MyStarsPage = createLazyRouteComponent(() => import('@/pages/dashboard/stars'), 'MyStarsPage')
const MySubscriptionsPage = createLazyRouteComponent(() => import('@/pages/dashboard/subscriptions'), 'MySubscriptionsPage')
const NotificationsPage = createLazyRouteComponent(() => import('@/pages/notifications'), 'NotificationsPage')
const TokensPage = createLazyRouteComponent(() => import('@/pages/dashboard/tokens'), 'TokensPage')
const CliAuthPage = createLazyRouteComponent(() => import('@/pages/cli-auth'), 'CliAuthPage')
const SecuritySettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/security'),
  'SecuritySettingsPage',
)
const ProfileSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/profile'),
  'ProfileSettingsPage',
)
const NotificationSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/notification-settings'),
  'NotificationSettingsPage',
)
const AdminUsersPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/users'),
  'AdminUsersPage',
  ['USER_ADMIN', 'SUPER_ADMIN'],
)
const AuditLogPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/audit-log'),
  'AuditLogPage',
  ['AUDITOR', 'SUPER_ADMIN'],
)
const AdminLabelsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/labels'),
  'AdminLabelsPage',
  ['SUPER_ADMIN'],
)

function DefaultNotFound() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
      Not Found
    </div>
  )
}

const rootRoute = createRootRoute({
  component: Layout,
  notFoundComponent: DefaultNotFound,
})

const requireAuth = createRequireAuth(getCurrentUser)

const landingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  validateSearch: (search: Record<string, unknown>): { onboarding?: boolean } => ({
    onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
  }),
  component: LandingPage,
})

const skillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'skills',
  validateSearch: (search: Record<string, unknown>): { onboarding?: boolean } => ({
    onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
  }),
  component: HomePage,
})

const agentsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'agents',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { onboarding?: boolean } => ({
    onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
  }),
  component: AgentsPage,
})

const toolsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'tools',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { onboarding?: boolean } => ({
    onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
  }),
  component: ToolsPage,
})

const catalogResourceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'catalog/$slug',
  beforeLoad: requireAuth,
  component: CatalogResourcePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'login',
  validateSearch: (search: Record<string, unknown>): { returnTo: string; reason?: string } => ({
    returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
    reason: typeof search.reason === 'string' ? search.reason : undefined,
  }),
  component: LoginPage,
})

const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'register',
  validateSearch: (search: Record<string, unknown>) => ({
    returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
  }),
  beforeLoad: ({ search }) => {
    throw redirect({
      to: '/login',
      search: {
        returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
      },
    })
  },
})

const resetPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'reset-password',
  beforeLoad: () => {
    throw redirect({ to: '/login', search: { returnTo: '' } })
  },
})

const privacyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'privacy',
  component: PrivacyPolicyPage,
})

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'search',
  component: SearchPage,
  validateSearch: (search: Record<string, unknown>): { q: string; namespace?: string; label?: string; sort: string; page: number; starredOnly: boolean; type?: 'ALL' | 'AGENT' | 'TOOL' | 'SKILL'; onboarding?: boolean } => {
    const resourceType = typeof search.type === 'string' && ['ALL', 'AGENT', 'TOOL', 'SKILL'].includes(search.type)
      ? search.type as 'ALL' | 'AGENT' | 'TOOL' | 'SKILL'
      : undefined
    return {
      q: normalizeSearchQuery(typeof search.q === 'string' ? search.q : ''),
      namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace.replace(/^@/, '') : undefined,
      label: typeof search.label === 'string' && search.label ? search.label : undefined,
      sort: (search.sort as string) || 'newest',
      page: Number(search.page) || 0,
      starredOnly: search.starredOnly === true || search.starredOnly === 'true',
      type: resourceType,
      onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
    }
  },
})

const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'terms',
  component: TermsOfServicePage,
})

const namespaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace',
  beforeLoad: requireAuth,
  component: NamespacePage,
})

const skillDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace/$slug',
  validateSearch: (search: Record<string, unknown>): { returnTo?: string } => ({
    returnTo: typeof search.returnTo === 'string' && search.returnTo.startsWith('/') ? search.returnTo : undefined,
  }),
  component: SkillDetailPage,
})

const skillVersionCompareRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace/$slug/compare',
  validateSearch: (search: Record<string, unknown>): { from: string; to: string } => ({
    from: typeof search.from === 'string' ? search.from : '',
    to: typeof search.to === 'string' ? search.to : '',
  }),
  component: SkillVersionComparePage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard',
  beforeLoad: requireAuth,
  component: DashboardPage,
})

const dashboardResourcesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/resources',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { onboarding?: boolean } => ({
    onboarding: search.onboarding === true || search.onboarding === 'true' ? true : undefined,
  }),
  component: ResourcesPage,
})

const dashboardSkillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/skills',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { page?: number; q?: string; namespace?: string; filter?: string } => ({
    page: typeof search.page === 'number' ? search.page : undefined,
    q: typeof search.q === 'string' && search.q ? search.q : undefined,
    namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace : undefined,
    filter: typeof search.filter === 'string' && search.filter ? search.filter : undefined,
  }),
  // Keep the old URL as a compatibility alias while the resource workspace becomes canonical.
  component: ResourcesPage,
})

const dashboardPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/publish',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { namespace?: string; visibility?: string } => ({
    namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace : undefined,
    visibility: typeof search.visibility === 'string' && search.visibility ? search.visibility : undefined,
  }),
  component: PublishPage,
})

const dashboardCatalogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/catalog',
  beforeLoad: requireAuth,
  // Keep the old URL as a compatibility alias while the resource workspace becomes canonical.
  component: ResourcesPage,
})

const dashboardCatalogPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/catalog/new',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { kind?: CatalogResourceKind } => ({
    kind: typeof search.kind === 'string' && CATALOG_RESOURCE_KINDS.includes(search.kind as CatalogResourceKind)
      ? search.kind as CatalogResourceKind
      : undefined,
  }),
  component: PublishResourcePage,
})

const dashboardCatalogEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/catalog/$slug/edit',
  beforeLoad: requireAuth,
  component: EditCatalogResourcePage,
})

const dashboardStarsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/stars',
  beforeLoad: requireAuth,
  component: MyStarsPage,
})

const dashboardSubscriptionsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/subscriptions',
  beforeLoad: requireAuth,
  component: MySubscriptionsPage,
})

const dashboardNotificationsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/notifications',
  beforeLoad: requireAuth,
  component: NotificationsPage,
})

const dashboardTokensRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/tokens',
  beforeLoad: async (ctx) => {
    await requireAuth(ctx)
    if (!isApiTokensEnabled()) {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: TokensPage,
})

const cliAuthRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'cli/auth',
  beforeLoad: () => {
    if (!isApiTokensEnabled()) {
      throw redirect({ to: '/' })
    }
  },
  component: CliAuthPage,
  validateSearch: (search: Record<string, unknown>): Record<string, string> => {
    // Preserve all CLI auth parameters - use empty string instead of undefined to prevent TanStack Router from removing them
    return {
      redirect_uri: typeof search.redirect_uri === 'string' ? search.redirect_uri : '',
      label_b64: typeof search.label_b64 === 'string' ? search.label_b64 : '',
      label: typeof search.label === 'string' ? search.label : '',
      state: typeof search.state === 'string' ? search.state : '',
    }
  },
})

const settingsSecurityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/security',
  beforeLoad: requireAuth,
  component: SecuritySettingsPage,
})

const settingsProfileRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/profile',
  beforeLoad: requireAuth,
  component: ProfileSettingsPage,
})

const settingsNotificationsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/notifications',
  beforeLoad: requireAuth,
  component: NotificationSettingsPage,
})

const settingsAccountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/accounts',
  beforeLoad: async (ctx) => {
    await requireAuth(ctx)
    throw redirect({ to: '/settings/security' })
  },
})

const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/users',
  beforeLoad: requireAuth,
  component: AdminUsersPage,
})

const adminAuditLogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/audit-log',
  beforeLoad: requireAuth,
  component: AuditLogPage,
})

const adminLabelsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/labels',
  beforeLoad: requireAuth,
  component: AdminLabelsPage,
})

const routeTree = rootRoute.addChildren([
  landingRoute,
  skillsRoute,
  agentsRoute,
  toolsRoute,
  catalogResourceRoute,
  loginRoute,
  registerRoute,
  resetPasswordRoute,
  privacyRoute,
  searchRoute,
  termsRoute,
  namespaceRoute,
  skillDetailRoute,
  skillVersionCompareRoute,
  dashboardRoute,
  dashboardResourcesRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardCatalogRoute,
  dashboardCatalogPublishRoute,
  dashboardCatalogEditRoute,
  dashboardStarsRoute,
  dashboardSubscriptionsRoute,
  dashboardNotificationsRoute,
  dashboardTokensRoute,
  cliAuthRoute,
  settingsSecurityRoute,
  settingsProfileRoute,
  settingsNotificationsRoute,
  settingsAccountsRoute,
  adminUsersRoute,
  adminAuditLogRoute,
  adminLabelsRoute,
])

export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: DefaultNotFound,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
