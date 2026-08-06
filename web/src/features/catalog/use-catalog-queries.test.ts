import { afterEach, describe, expect, it, vi } from 'vitest'
import { catalogApi } from '@/api/client'
import type { CatalogResourceDetail, CatalogResourceRequest } from '@/api/types'
import { createCatalogResource, updateCatalogResource } from './use-catalog-queries'

const request: CatalogResourceRequest = {
  name: 'Demo tool',
  summary: 'A managed static tool',
  kind: 'ONLINE_TOOL',
  version: '1.0.0',
  publish: true,
}

const draft = { slug: 'demo-tool', status: 'DRAFT' } as CatalogResourceDetail
const stored = { ...draft, artifactAvailable: true } as CatalogResourceDetail
const published = {
  ...stored,
  status: 'PUBLISHED',
  accessUrl: 'http://localhost:8090/apps/demo-tool/',
} as CatalogResourceDetail
const artifact = { name: 'demo.zip' } as File

describe('catalog managed publishing flow', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a draft, uploads the artifact, then publishes through the Catalog lifecycle endpoint', async () => {
    const create = vi.spyOn(catalogApi, 'create').mockResolvedValue(draft)
    const upload = vi.spyOn(catalogApi, 'uploadArtifact').mockResolvedValue(stored)
    const publish = vi.spyOn(catalogApi, 'publish').mockResolvedValue(published)

    const result = await createCatalogResource({ request, artifact, publishVersion: '1.0.0' })

    expect(create).toHaveBeenCalledWith({ ...request, publish: false })
    expect(upload).toHaveBeenCalledWith('demo-tool', artifact)
    expect(publish).toHaveBeenCalledWith('demo-tool', '1.0.0')
    expect(create.mock.invocationCallOrder[0]).toBeLessThan(upload.mock.invocationCallOrder[0])
    expect(upload.mock.invocationCallOrder[0]).toBeLessThan(publish.mock.invocationCallOrder[0])
    expect(result).toBe(published)
  })

  it('deploys a replacement artifact only after the Catalog update and upload complete', async () => {
    const update = vi.spyOn(catalogApi, 'update').mockResolvedValue(draft)
    const upload = vi.spyOn(catalogApi, 'uploadArtifact').mockResolvedValue(stored)
    const publish = vi.spyOn(catalogApi, 'publish').mockResolvedValue(published)

    await updateCatalogResource({
      slug: 'demo-tool',
      request: { ...request, publish: false },
      artifact,
      publishVersion: '2.0.0',
    })

    expect(update).toHaveBeenCalledWith('demo-tool', { ...request, publish: false })
    expect(upload).toHaveBeenCalledWith('demo-tool', artifact)
    expect(publish).toHaveBeenCalledWith('demo-tool', '2.0.0')
    expect(update.mock.invocationCallOrder[0]).toBeLessThan(upload.mock.invocationCallOrder[0])
    expect(upload.mock.invocationCallOrder[0]).toBeLessThan(publish.mock.invocationCallOrder[0])
  })
})
