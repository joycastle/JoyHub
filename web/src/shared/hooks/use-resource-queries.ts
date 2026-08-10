import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, resourcesApi } from '@/api/client'
import type { ResourceStats } from '@/api/types'
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
    onMutate: async ({ resourceId, favorited }) => {
      const favoriteKey = ['resources', resourceId, 'favorite']
      const statsKey = ['resources', resourceId, 'stats']
      await queryClient.cancelQueries({ queryKey: favoriteKey })
      const previousFavorite = queryClient.getQueryData<boolean>(favoriteKey)
      const previousStats = queryClient.getQueryData<ResourceStats>(statsKey)
      const nextFavorite = !favorited
      queryClient.setQueryData(favoriteKey, nextFavorite)
      if (previousStats) {
        queryClient.setQueryData<ResourceStats>(statsKey, {
          ...previousStats,
          favoriteCount: Math.max(0, previousStats.favoriteCount + (nextFavorite ? 1 : -1)),
          favorited: nextFavorite,
        })
      }
      return { favoriteKey, statsKey, previousFavorite, previousStats }
    },
    onError: (_error, _variables, context) => {
      if (!context) return
      queryClient.setQueryData(context.favoriteKey, context.previousFavorite)
      queryClient.setQueryData(context.statsKey, context.previousStats)
    },
    onSuccess: (favorited, { resourceId }) => {
      queryClient.setQueryData(['resources', resourceId, 'favorite'], favorited)
      void queryClient.invalidateQueries({ queryKey: ['resources', resourceId, 'stats'] })
      void queryClient.invalidateQueries({ queryKey: ['resources', 'mine'] })
      void queryClient.invalidateQueries({ queryKey: ['skills'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
    },
  })
}

export function useResourceStats(resourceId: string, enabled = true, recordView = true) {
  return useQuery<ResourceStats>({
    queryKey: ['resources', resourceId, 'stats'],
    queryFn: async () => {
      // A counter must never make the public detail page unusable when analytics is temporarily
      // unavailable (for example during a rolling deployment).
      if (recordView) {
        try {
          await resourcesApi.recordView(resourceId)
        } catch {
          // Continue with the authoritative snapshot below.
        }
      }
      return resourcesApi.stats(resourceId)
    },
    enabled: Boolean(resourceId) && enabled,
    staleTime: 30_000,
  })
}

export function useRecordResourceUse() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resourceId: string) => resourcesApi.recordUse(resourceId),
    onSuccess: (_value, resourceId) => {
      const statsKey = ['resources', resourceId, 'stats']
      const current = queryClient.getQueryData<ResourceStats>(statsKey)
      if (current) {
        queryClient.setQueryData<ResourceStats>(statsKey, {
          ...current,
          useCount: current.useCount + 1,
        })
      }
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
