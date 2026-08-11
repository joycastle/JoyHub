import { useQuery } from '@tanstack/react-query'
import { resourcesApi } from '@/api/client'

/** The single target list shared by Skill, Tool, and Agent publishing. */
export function usePublishTargets() {
  return useQuery({
    queryKey: ['publish-targets'],
    queryFn: () => resourcesApi.publishTargets(),
    staleTime: 5 * 60 * 1000,
  })
}
