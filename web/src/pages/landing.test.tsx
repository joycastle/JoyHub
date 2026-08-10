import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({}),
}))

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({ data: [] }),
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

vi.mock('@/shared/components/brand-mark', () => ({
  BrandMark: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: { items: [] },
    isLoading: false,
  }),
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))

vi.mock('@/features/search/use-unified-resource-search', () => ({
  useResourceRecommendations: () => ({ data: [] }),
  useUnifiedResourceSearch: () => ({ data: { items: [] } }),
}))

vi.mock('@/shared/hooks/use-in-view', () => ({
  useInView: () => ({ ref: vi.fn(), inView: true }),
}))

vi.mock('@/shared/lib/search-query', () => ({
  MAX_SEARCH_INPUT_LENGTH: 200,
  normalizeSearchQuery: (q: string) => q.trim(),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LandingPage } from './landing'

describe('LandingPage', () => {
  it('exports a named component function', () => {
    expect(typeof LandingPage).toBe('function')
  })

  it('renders the unified JoyHub capability entry', () => {
    const html = renderToStaticMarkup(<LandingPage />)

    expect(html).toContain('joyhubHome.title')
    expect(html).toContain('JOYHUB MARKETPLACE')
    expect(html).toContain('DISCOVER')
  })
})
