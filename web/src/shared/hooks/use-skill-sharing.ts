import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { skillSharingApi } from '@/api/client'

export function useSkillSharing(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'sharing'],
    queryFn: () => skillSharingApi.list(namespace, slug),
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useUpdateSkillSharing() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, namespaceIds, restricted }: {
      namespace: string
      slug: string
      namespaceIds: number[]
      restricted: boolean
    }) => skillSharingApi.replace(namespace, slug, namespaceIds, restricted),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'sharing'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'search'] })
    },
    meta: {
      skipGlobalErrorHandler: true,
    },
  })
}
