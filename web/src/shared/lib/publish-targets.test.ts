import { describe, expect, it } from 'vitest'
import type { PublishTarget } from '@/api/types'
import { resolveDefaultPublishTarget } from './publish-targets'

function target(id: number, slug: string): PublishTarget {
  return { id, slug, displayName: slug, supportedResourceTypes: ['SKILL', 'TOOL', 'AGENT'] }
}

describe('resolveDefaultPublishTarget', () => {
  it('selects the public library even when the response order changes', () => {
    expect(resolveDefaultPublishTarget([target(1, 'team-a'), target(2, 'global')])?.slug).toBe('global')
  })

  it('falls back to the first available department', () => {
    expect(resolveDefaultPublishTarget([target(1, 'team-a')])?.slug).toBe('team-a')
  })
})
