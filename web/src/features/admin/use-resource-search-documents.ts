import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

export function useResourceSearchDocuments(params: { resourceType?: string; generationStatus?: string; page?: number; size?: number }) {
  return useQuery({ queryKey: ['admin', 'resource-search-documents', params], queryFn: () => adminApi.getResourceSearchDocuments(params) })
}

export function useResourceSearchDocument(resourceType: string, resourceId: number) {
  return useQuery({
    queryKey: ['admin', 'resource-search-document', resourceType, resourceId],
    queryFn: () => adminApi.getResourceSearchDocument(resourceType, resourceId),
  })
}

export function useRegenerateResourceSearchDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ resourceType, resourceId }: { resourceType: string; resourceId: number }) =>
      adminApi.regenerateResourceSearchDocument(resourceType, resourceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'resource-search-documents'] }),
  })
}
