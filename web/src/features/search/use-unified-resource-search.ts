import { useQuery } from '@tanstack/react-query'
import { resourcesApi } from '@/api/client'
import type { UnifiedResourceSearchType } from '@/api/types'

export interface UnifiedResourceSearchParams {
  q?: string
  namespace?: string
  label?: string
  sort?: string
  type?: UnifiedResourceSearchType
  starredOnly?: boolean
  page?: number
  size?: number
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
