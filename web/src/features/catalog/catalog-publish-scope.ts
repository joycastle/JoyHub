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
    ? '发布后所有人可见。'
    : '发布到项目空间后，该空间内所有人可见。'
}
