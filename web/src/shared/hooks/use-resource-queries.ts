import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, resourcesApi } from '@/api/client'
import { useQuery } from '@tanstack/react-query'

export function useResourceLifecycleAction(action: 'archive' | 'unarchive' | 'publish' | 'offline') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ resourceId, version }: { resourceId: string; version?: string }) => {
      if (action === 'archive') return resourcesApi.archive(resourceId)
      if (action === 'unarchive') return resourcesApi.unarchive(resourceId)
      if (action === 'publish') return resourcesApi.publish(resourceId, version)
      return resourcesApi.offline(resourceId)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['resources', 'mine'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
      void queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useToggleResourceFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ resourceId, favorited }: { resourceId: string; favorited: boolean }) => (
      favorited ? resourcesApi.unfavorite(resourceId) : resourcesApi.favorite(resourceId)
    ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['resources', 'mine'] })
      void queryClient.invalidateQueries({ queryKey: ['skills'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
    },
  })
}

export function useResourceFavorite(resourceId: string, enabled = true) {
  return useQuery({
    queryKey: ['resources', resourceId, 'favorite'],
    queryFn: async () => {
      try {
        return await resourcesApi.favoriteState(resourceId)
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return false
        throw error
      }
    },
    enabled: Boolean(resourceId) && enabled,
  })
}
