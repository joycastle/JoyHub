import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => ({}),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/search/search-bar', () => ({
  SearchBar: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: { userId: 'test-user' } }),
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/components/brand-mark', () => ({
  BrandMark: () => null,
}))

vi.mock('@/features/search/use-unified-resource-search', () => ({
  useUnifiedResourceSearch: () => ({
    data: { items: [], total: 0, page: 0, size: 12 },
    isLoading: false,
    isError: false,
    isFetching: false,
  }),
}))

vi.mock('@/shared/lib/search-query', () => ({
  normalizeSearchQuery: (q: string) => q.trim(),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { HomePage } from './home'

describe('HomePage', () => {
  it('exports a named component function', () => {
    expect(typeof HomePage).toBe('function')
  })

  it('renders the dedicated skill center header', () => {
    const html = renderToStaticMarkup(<HomePage />)

    expect(html).toContain('skillCenter.title')
    expect(html).toContain('skillCenter.description')
    expect(html).toContain('skillCenter.publish')
    expect(html).toContain('resourceCenter.resultCount')
  })
})
