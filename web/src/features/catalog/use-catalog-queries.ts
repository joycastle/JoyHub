import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '@/api/client'
import type { CatalogCenter, CatalogResourceKind, CatalogResourceRequest } from '@/api/types'

export const catalogKeys = {
  all: ['catalog'] as const,
  lists: () => [...catalogKeys.all, 'list'] as const,
  list: (params: object) => [...catalogKeys.lists(), params] as const,
  mine: () => [...catalogKeys.all, 'mine'] as const,
  detail: (slug: string) => [...catalogKeys.all, 'detail', slug] as const,
}

export function useCatalogResources(params: {
  q?: string
  center?: CatalogCenter
  kind?: CatalogResourceKind
  scenario?: string
  departmentId?: number
  page?: number
  size?: number
  enabled?: boolean
} = {}) {
  const { enabled = true, ...queryParams } = params
  return useQuery({
    queryKey: catalogKeys.list(queryParams),
    queryFn: () => catalogApi.list(queryParams),
    enabled,
  })
}

export function useCatalogResource(slug: string) {
  return useQuery({
    queryKey: catalogKeys.detail(slug),
    queryFn: () => catalogApi.detail(slug),
    enabled: Boolean(slug),
  })
}

export function useMyCatalogResources() {
  return useQuery({ queryKey: catalogKeys.mine(), queryFn: () => catalogApi.mine() })
}

export function useCreateCatalogResource() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ request, artifact }: { request: CatalogResourceRequest; artifact?: File }) => {
      const resource = await catalogApi.create(request)
      return artifact ? catalogApi.uploadArtifact(resource.slug, artifact) : resource
    },
    onSuccess: (resource) => {
      client.setQueryData(catalogKeys.detail(resource.slug), resource)
      void client.invalidateQueries({ queryKey: catalogKeys.lists() })
      void client.invalidateQueries({ queryKey: catalogKeys.mine() })
    },
  })
}

export function useUpdateCatalogResource() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ slug, request, artifact }: { slug: string; request: CatalogResourceRequest; artifact?: File }) => {
      const resource = await catalogApi.update(slug, request)
      return artifact ? catalogApi.uploadArtifact(resource.slug, artifact) : resource
    },
    onSuccess: (resource) => {
      client.setQueryData(catalogKeys.detail(resource.slug), resource)
      void client.invalidateQueries({ queryKey: catalogKeys.lists() })
      void client.invalidateQueries({ queryKey: catalogKeys.mine() })
    },
  })
}

export function useCatalogLifecycleAction(action: 'publish' | 'offline') {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (slug: string) => catalogApi[action](slug),
    onSuccess: (resource) => {
      client.setQueryData(catalogKeys.detail(resource.slug), resource)
      void client.invalidateQueries({ queryKey: catalogKeys.lists() })
      void client.invalidateQueries({ queryKey: catalogKeys.mine() })
    },
  })
}
