import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '@/api/client'
import type { CatalogCenter, CatalogResourceKind, CatalogResourceRequest } from '@/api/types'

export const catalogKeys = {
  all: ['catalog'] as const,
  lists: () => [...catalogKeys.all, 'list'] as const,
  list: (params: object) => [...catalogKeys.lists(), params] as const,
  mineRoot: () => [...catalogKeys.all, 'mine'] as const,
  mine: (params: object = {}) => [...catalogKeys.mineRoot(), params] as const,
  detail: (slug: string) => [...catalogKeys.all, 'detail', slug] as const,
}

interface SaveCatalogResourceInput {
  request: CatalogResourceRequest
  artifact?: File
  publishVersion?: string
}

interface UpdateCatalogResourceInput extends SaveCatalogResourceInput {
  slug: string
}

export async function createCatalogResource({
  request,
  artifact,
  publishVersion,
}: SaveCatalogResourceInput) {
  const resource = await catalogApi.create(publishVersion ? { ...request, publish: false } : request)
  const stored = artifact ? await catalogApi.uploadArtifact(resource.slug, artifact) : resource
  return publishVersion ? catalogApi.publish(stored.slug, publishVersion) : stored
}

export async function updateCatalogResource({
  slug,
  request,
  artifact,
  publishVersion,
}: UpdateCatalogResourceInput) {
  const resource = await catalogApi.update(slug, request)
  const stored = artifact ? await catalogApi.uploadArtifact(resource.slug, artifact) : resource
  return publishVersion ? catalogApi.publish(stored.slug, publishVersion) : stored
}

export function useCatalogResources(params: {
  q?: string
  center?: CatalogCenter
  kind?: CatalogResourceKind
  scenario?: string
  departmentId?: number
  sort?: 'recommended' | 'newest'
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

export function useMyCatalogResources(params: { page?: number; size?: number } = {}) {
  return useQuery({ queryKey: catalogKeys.mine(params), queryFn: () => catalogApi.mine(params) })
}

export function useCreateCatalogResource() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: createCatalogResource,
    onSuccess: (resource) => {
      client.setQueryData(catalogKeys.detail(resource.slug), resource)
      void client.invalidateQueries({ queryKey: catalogKeys.lists() })
      void client.invalidateQueries({ queryKey: catalogKeys.mineRoot() })
    },
  })
}

export function useUpdateCatalogResource() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: updateCatalogResource,
    onSuccess: (resource) => {
      client.setQueryData(catalogKeys.detail(resource.slug), resource)
      void client.invalidateQueries({ queryKey: catalogKeys.lists() })
      void client.invalidateQueries({ queryKey: catalogKeys.mineRoot() })
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
      void client.invalidateQueries({ queryKey: catalogKeys.mineRoot() })
    },
  })
}
