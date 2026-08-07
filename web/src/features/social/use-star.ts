import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'
import type { ResourceStats } from '@/api/types'

interface StarStatus {
  starred: boolean
}

/**
 * Star-state hooks for one skill.
 *
 * Anonymous users are treated as unstarred instead of surfacing authorization failures into the UI.
 */
async function getStarStatus(skillId: number): Promise<StarStatus> {
  try {
    const starred = await fetchJson<boolean>(`${WEB_API_PREFIX}/resources/skill:${skillId}/favorite`)
    return { starred }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { starred: false }
    }
    throw error
  }
}

async function toggleStar(skillId: number, starred: boolean): Promise<void> {
  if (starred) {
    await fetchJson<void>(`${WEB_API_PREFIX}/resources/skill:${skillId}/favorite`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  } else {
    await fetchJson<void>(`${WEB_API_PREFIX}/resources/skill:${skillId}/favorite`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  }
}

export function useStar(skillId: number, enabled = true) {
  return useQuery({
    queryKey: ['skills', skillId, 'star'],
    queryFn: () => getStarStatus(skillId),
    enabled: !!skillId && enabled,
  })
}

export function useToggleStar(skillId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (starred: boolean) => toggleStar(skillId, starred),
    onMutate: async (starred) => {
      const starKey = ['skills', skillId, 'star']
      const statsKey = ['resources', `skill:${skillId}`, 'stats']
      await queryClient.cancelQueries({ queryKey: starKey })
      const previousStar = queryClient.getQueryData<StarStatus>(starKey)
      const previousStats = queryClient.getQueryData<ResourceStats>(statsKey)
      const nextStarred = !starred
      queryClient.setQueryData<StarStatus>(starKey, { starred: nextStarred })
      if (previousStats) {
        queryClient.setQueryData<ResourceStats>(statsKey, {
          ...previousStats,
          favoriteCount: Math.max(0, previousStats.favoriteCount + (nextStarred ? 1 : -1)),
          favorited: nextStarred,
        })
      }
      return { starKey, statsKey, previousStar, previousStats }
    },
    onError: (_error, _starred, context) => {
      if (!context) return
      queryClient.setQueryData(context.starKey, context.previousStar)
      queryClient.setQueryData(context.statsKey, context.previousStats)
    },
    onSuccess: () => {
      // Star actions affect both the local button state and starred-skill collections elsewhere in
      // the app. The optimistic value remains visible until the server state is refreshed.
      queryClient.invalidateQueries({ queryKey: ['resources', `skill:${skillId}`, 'stats'] })
      queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'star'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'stars'] })
    },
  })
}
