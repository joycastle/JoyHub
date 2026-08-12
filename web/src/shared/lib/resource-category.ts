import type { TFunction } from 'i18next'

/**
 * The single user-facing resource classification used by Skills, Agents, and Tools.
 * `undefined`/`null` means the publisher requested AI classification.
 */
export const RESOURCE_CATEGORY_OPTIONS = [
  { code: 'GAME_DEV_QA', labelKey: 'resourceCategory.options.GAME_DEV_QA' },
  { code: 'UA_MONETIZATION', labelKey: 'resourceCategory.options.UA_MONETIZATION' },
  { code: 'CREATIVE_MEDIA', labelKey: 'resourceCategory.options.CREATIVE_MEDIA' },
  { code: 'DATA_ANALYTICS', labelKey: 'resourceCategory.options.DATA_ANALYTICS' },
  { code: 'COLLAB_PRODUCTIVITY', labelKey: 'resourceCategory.options.COLLAB_PRODUCTIVITY' },
  { code: 'AI_ENGINEERING', labelKey: 'resourceCategory.options.AI_ENGINEERING' },
  { code: 'INTEGRATION_AUTOMATION', labelKey: 'resourceCategory.options.INTEGRATION_AUTOMATION' },
  { code: 'GENERAL_KNOWLEDGE', labelKey: 'resourceCategory.options.GENERAL_KNOWLEDGE' },
  { code: 'OTHER', labelKey: 'resourceCategory.options.OTHER' },
] as const

export type ResourceCategoryCode = typeof RESOURCE_CATEGORY_OPTIONS[number]['code']

export const RESOURCE_CATEGORY_CODES = RESOURCE_CATEGORY_OPTIONS.map(({ code }) => code)
export const AI_RESOURCE_CATEGORY = '' as const

export function isResourceCategoryCode(value: unknown): value is ResourceCategoryCode {
  return typeof value === 'string' && RESOURCE_CATEGORY_CODES.includes(value as ResourceCategoryCode)
}

export function resourceCategoryLabel(t: TFunction, code?: string | null): string {
  const option = RESOURCE_CATEGORY_OPTIONS.find((item) => item.code === code)
  return option ? t(option.labelKey) : t('resourceCategory.aiOption')
}
