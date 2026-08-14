import type { CatalogVisibilityScope, PublishTarget } from '@/api/types'

export interface CatalogPublishScope {
  visibilityScope: CatalogVisibilityScope
  visibleDepartmentIds: number[]
}

export function resolveCatalogPublishScope(target: PublishTarget | undefined): CatalogPublishScope {
  if (!target || target.slug === 'global') {
    return { visibilityScope: 'COMPANY', visibleDepartmentIds: [] }
  }
  return { visibilityScope: 'DEPARTMENTS', visibleDepartmentIds: [target.id] }
}

export function catalogPublishScopeHint(target: PublishTarget | undefined): string {
  return target?.slug === 'global'
    ? '发布到公共库后，全公司人员可见。'
    : '发布到部门库后，所属部门内全体人员可见。'
}
