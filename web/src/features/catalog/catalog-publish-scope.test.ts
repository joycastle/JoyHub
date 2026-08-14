import { describe, expect, it } from 'vitest'
import type { PublishTarget } from '@/api/types'
import { catalogPublishScopeHint, resolveCatalogPublishScope } from './catalog-publish-scope'

function target(id: number, slug: string): PublishTarget {
  return { id, slug, displayName: slug, supportedResourceTypes: ['TOOL', 'AGENT'] }
}

describe('catalog publish scope', () => {
  it('makes the public library visible to the whole company', () => {
    expect(resolveCatalogPublishScope(target(1, 'global'))).toEqual({
      visibilityScope: 'COMPANY',
      visibleDepartmentIds: [],
    })
    expect(catalogPublishScopeHint(target(1, 'global'))).toContain('全公司人员可见')
  })

  it('limits a department library to everyone in that department', () => {
    expect(resolveCatalogPublishScope(target(8, 'lab'))).toEqual({
      visibilityScope: 'DEPARTMENTS',
      visibleDepartmentIds: [8],
    })
    expect(catalogPublishScopeHint(target(8, 'lab'))).toContain('所属部门内全体人员可见')
  })
})
