import { describe, expect, it } from 'vitest'
import { buildDiscoveryRecommendation } from './recommendation-engine'

describe('buildDiscoveryRecommendation', () => {
  it('prioritizes visible catalog resources and fills the remaining slots with skills', () => {
    const result = buildDiscoveryRecommendation({
      question: '我想做数据分析',
      catalog: [{
        id: 1,
        slug: 'report-agent',
        name: '报表 Agent',
        summary: '自动生成报表',
        kind: 'AGENT',
        accessUrl: 'https://reports.example.com',
      }],
      skills: [{
        id: 2,
        slug: 'spreadsheet',
        displayName: '表格分析',
        summary: '分析表格数据',
        namespace: 'global',
        downloadCount: 0,
        starCount: 0,
        ratingCount: 0,
        updatedAt: '2026-08-04T00:00:00Z',
        canSubmitPromotion: false,
      }],
    })

    expect(result.suggestions.map((item) => item.title)).toEqual(['报表 Agent', '表格分析'])
    expect(result.suggestions[0]).toMatchObject({ accessUrl: 'https://reports.example.com' })
    expect(result.summary).toContain('1 个 Agent')
    expect(result.followUps[0]).toContain('数据分析')
  })

  it('provides recovery guidance when no visible resource matches', () => {
    const result = buildDiscoveryRecommendation({ question: '非常具体的需求', catalog: [], skills: [] })

    expect(result.suggestions).toEqual([])
    expect(result.summary).toContain('换成“想完成什么”')
  })

  it('returns English guidance when the interface language is English', () => {
    const result = buildDiscoveryRecommendation({
      question: 'Help me analyze data',
      catalog: [],
      skills: [],
      language: 'en-US',
    })

    expect(result.summary).toContain('Describe the outcome')
    expect(result.followUps[0]).toContain('Agent')
  })
})
