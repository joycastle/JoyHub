import { describe, expect, it } from 'vitest'
import { getPublishRequestTimeoutMs } from './use-skill-queries'

describe('getPublishRequestTimeoutMs', () => {
  it('allows at least five minutes for small uploads', () => {
    expect(getPublishRequestTimeoutMs([{ size: 4.5 * 1024 * 1024 }])).toBe(5 * 60_000)
  })

  it('scales the timeout with the total batch size', () => {
    const timeout = getPublishRequestTimeoutMs([
      { size: 40 * 1024 * 1024 },
      { size: 40 * 1024 * 1024 },
    ])

    expect(timeout).toBe(1_400_000)
  })

  it('caps the timeout at thirty minutes', () => {
    expect(getPublishRequestTimeoutMs([{ size: 200 * 1024 * 1024 }])).toBe(30 * 60_000)
  })
})
