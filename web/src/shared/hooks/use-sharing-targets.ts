import { useQuery } from '@tanstack/react-query'
import { sharingTargetsApi } from '@/api/client'

export function useSharingTargets(enabled = true) {
  return useQuery({
    queryKey: ['sharing-targets'],
    queryFn: sharingTargetsApi.list,
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}
