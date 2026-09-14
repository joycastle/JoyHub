import { describe, expect, it } from 'vitest'
import type { PublishTarget } from '@/api/types'
import { catalogPublishScopeHint, resolveCatalogPublishScope } from './catalog-publish-scope'

function target(id: number, slug: string): PublishTarget {
  return { id, slug, displayName: slug, supportedResourceTypes: ['TOOL', 'AGENT'] }
}

describe('catalog publish scope', () => {
  it('allows a public-library resource to be restricted to selected departments', () => {
    expect(resolveCatalogPublishScope(target(1, 'global'), [8, 9], true)).toEqual({
      visibilityScope: 'DEPARTMENTS', visibleDepartmentIds: [8, 9],
    })
  })
  it('shares with several departments while retaining the maintenance department', () => {
    expect(resolveCatalogPublishScope(target(8, 'lab'), [9, 10, 8])).toEqual({
      visibilityScope: 'DEPARTMENTS', visibleDepartmentIds: [8, 9, 10],
    })
  })
  it('makes the public library visible to the whole company', () => {
    expect(resolveCatalogPublishScope(target(1, 'global'))).toEqual({
      visibilityScope: 'COMPANY',
      visibleDepartmentIds: [],
    })
    expect(catalogPublishScopeHint(target(1, 'global'))).toContain('所有人可见')
  })

  it('limits a department library to everyone in that department', () => {
    expect(resolveCatalogPublishScope(target(8, 'lab'))).toEqual({
      visibilityScope: 'DEPARTMENTS',
      visibleDepartmentIds: [8],
    })
    expect(catalogPublishScopeHint(target(8, 'lab'))).toContain('该空间内所有人可见')
  })
})
