import { useQuery } from '@tanstack/react-query'
import { resourcesApi } from '@/api/client'
import type { UnifiedResourceSearchType } from '@/api/types'
import type { ResourceCategoryCode } from '@/shared/lib/resource-category'

export interface UnifiedResourceSearchParams {
  q?: string
  namespace?: string
  label?: string
  categoryCode?: ResourceCategoryCode | string
  sort?: string
  type?: UnifiedResourceSearchType
  accessMode?: Array<'INSTALL' | 'OPEN' | 'DOWNLOAD'>
  starredOnly?: boolean
  page?: number
  size?: number
}

export function useResourceRecommendations(size = 12) {
  return useQuery({
    queryKey: ['resources', 'recommendations', size],
    queryFn: () => resourcesApi.recommendations(size),
  })
}

export function useUnifiedResourceSearch(
  params: UnifiedResourceSearchParams,
  enabled = true,
) {
  return useQuery({
    queryKey: ['resources', 'search', params],
    queryFn: () => resourcesApi.search(params),
    enabled,
  })
}
