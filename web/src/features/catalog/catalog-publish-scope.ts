import type { CatalogVisibilityScope, PublishTarget } from '@/api/types'

export interface CatalogPublishScope {
  visibilityScope: CatalogVisibilityScope
  visibleDepartmentIds: number[]
}

export function resolveCatalogPublishScope(target: PublishTarget | undefined, selectedDepartmentIds: number[] = [], restrictPublicLibrary = false): CatalogPublishScope {
  if (!target || (target.slug === 'global' && !restrictPublicLibrary)) {
    return { visibilityScope: 'COMPANY', visibleDepartmentIds: [] }
  }
  return { visibilityScope: 'DEPARTMENTS', visibleDepartmentIds: [...new Set([...(target.slug === 'global' ? [] : [target.id]), ...selectedDepartmentIds])] }
}

export function catalogPublishScopeHint(target: PublishTarget | undefined): string {
  return target?.slug === 'global'
    ? '发布后所有人可见。'
    : '发布到项目空间后，该空间内所有人可见。'
}
