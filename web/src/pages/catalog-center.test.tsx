import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => ({}),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/features/search/search-bar', () => ({ SearchBar: () => null }))
vi.mock('@/entities/catalog-resource/catalog-resource-card', () => ({ CatalogResourceCard: () => null }))
vi.mock('@/features/onboarding/center-feature-tour', () => ({ CenterFeatureTour: () => null }))
vi.mock('@/features/catalog/common-tools', () => ({
  useCommonTools: () => ({ isCommonTool: () => false, recordToolUse: vi.fn(), toggleTool: vi.fn() }),
}))
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: { userId: 'test-user' } }),
}))
vi.mock('@/features/search/use-unified-resource-search', () => ({
  useUnifiedResourceSearch: () => ({
    data: { items: [], total: 0, page: 0, size: 12 },
    isLoading: false,
    isError: false,
    isFetching: false,
  }),
}))
vi.mock('@/shared/components/skeleton-loader', () => ({ SkeletonList: () => null }))
vi.mock('@/shared/ui/button', () => ({ Button: ({ children }: { children: unknown }) => children }))

import { AgentsPage, ToolsPage } from './catalog-center'

describe('Catalog centers', () => {
  it('renders the Agent center through the shared discovery frame', () => {
    const html = renderToStaticMarkup(<AgentsPage />)

    expect(html).toContain('agentCenter.title')
    expect(html).toContain('agentCenter.description')
    expect(html).toContain('resourceCenter.resultCount')
  })

  it('renders the Tool center through the shared discovery frame', () => {
    const html = renderToStaticMarkup(<ToolsPage />)

    expect(html).toContain('toolCenter.title')
    expect(html).toContain('toolCenter.description')
    expect(html).toContain('resourceCenter.resultCount')
  })
})
