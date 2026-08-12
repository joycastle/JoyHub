import { describe, expect, it } from 'vitest'
import { RESOURCE_CATEGORY_OPTIONS, isResourceCategoryCode } from './resource-category'

describe('resource category pool', () => {
  it('contains the fixed cross-resource pool and OTHER', () => {
    expect(RESOURCE_CATEGORY_OPTIONS.map(({ code }) => code)).toEqual([
      'GAME_DEV_QA', 'UA_MONETIZATION', 'CREATIVE_MEDIA', 'DATA_ANALYTICS',
      'COLLAB_PRODUCTIVITY', 'AI_ENGINEERING', 'INTEGRATION_AUTOMATION',
      'GENERAL_KNOWLEDGE', 'OTHER',
    ])
  })

  it('rejects free-form values', () => {
    expect(isResourceCategoryCode('数据分析')).toBe(false)
    expect(isResourceCategoryCode('DATA_ANALYTICS')).toBe(true)
  })
})
