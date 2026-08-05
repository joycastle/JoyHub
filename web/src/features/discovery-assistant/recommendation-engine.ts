import type { CatalogResourceSummary, SkillSummary } from '@/api/types'

interface DiscoveryEvidence {
  accessUrl?: string
  usage?: string
  evidence?: string
  source?: string
}

export type DiscoverySuggestion =
  | ({ type: 'catalog'; id: number; title: string; description: string; kind: CatalogResourceSummary['kind']; slug: string } & DiscoveryEvidence)
  | ({ type: 'skill'; id: number; title: string; description: string; namespace: string; slug: string } & DiscoveryEvidence)

export interface DiscoveryRecommendation {
  summary: string
  suggestions: DiscoverySuggestion[]
  followUps: string[]
}

interface RecommendationInput {
  question: string
  catalog: CatalogResourceSummary[]
  skills: SkillSummary[]
  language?: string
}

const ZH_INTENT_FOLLOW_UPS: Array<{ keywords: string[]; prompts: string[] }> = [
  {
    keywords: ['数据', '报表', '分析', '指标', 'sql', 'excel'],
    prompts: ['帮我找数据分析工具', '有没有自动生成报表的能力', '推荐适合分析数据的 Skill'],
  },
  {
    keywords: ['研发', '代码', '开发', '测试', '部署', '接口'],
    prompts: ['找一个研发提效 Agent', '推荐代码审查 Skill', '有什么自动化测试工具'],
  },
  {
    keywords: ['文档', '写作', '总结', '会议', '方案'],
    prompts: ['帮我整理会议纪要', '推荐写方案的 Skill', '找一个文档助手'],
  },
]

const DEFAULT_FOLLOW_UPS = ['帮我找一个能直接使用的 Agent', '推荐研发提效工具', '有哪些适合写文档的 Skill']
const EN_DEFAULT_FOLLOW_UPS = ['Find an Agent I can use now', 'Recommend developer productivity tools', 'What Skills can help me write documents?']

export function buildDiscoveryRecommendation({ question, catalog, skills, language = 'zh' }: RecommendationInput): DiscoveryRecommendation {
  const normalized = question.trim().toLowerCase()
  const isEnglish = language.toLowerCase().startsWith('en')
  const suggestions: DiscoverySuggestion[] = [
    ...catalog.slice(0, 3).map((resource): DiscoverySuggestion => ({
      type: 'catalog',
      id: resource.id,
      title: resource.name,
      description: resource.summary,
      kind: resource.kind,
      slug: resource.slug,
      accessUrl: resource.accessUrl,
    })),
    ...skills.slice(0, Math.max(0, 4 - Math.min(catalog.length, 3))).map((skill): DiscoverySuggestion => ({
      type: 'skill',
      id: skill.id,
      title: skill.displayName,
      description: skill.summary || `@${skill.namespace}/${skill.slug}`,
      namespace: skill.namespace,
      slug: skill.slug,
    })),
  ]

  const followUps = isEnglish
    ? EN_DEFAULT_FOLLOW_UPS
    : ZH_INTENT_FOLLOW_UPS.find(({ keywords }) => keywords.some((keyword) => normalized.includes(keyword)))?.prompts
      ?? DEFAULT_FOLLOW_UPS

  if (suggestions.length === 0) {
    return {
      summary: isEnglish
        ? 'I could not find an exact match yet. Describe the outcome you want, or remove product and department names to broaden the search.'
        : '暂时没有找到完全匹配的能力。可以换成“想完成什么”来描述，或减少产品名称、部门名称等限制。',
      suggestions,
      followUps,
    }
  }

  const agentCount = catalog.filter((resource) => resource.kind === 'AGENT').length
  const toolCount = catalog.length - agentCount
  const parts = [
    agentCount > 0 ? `${agentCount} 个 Agent` : '',
    toolCount > 0 ? `${toolCount} 个工具` : '',
    skills.length > 0 ? `${skills.length} 个 Skill` : '',
  ].filter(Boolean)

  return {
    summary: isEnglish
      ? `I found ${[
          agentCount > 0 ? `${agentCount} Agent${agentCount === 1 ? '' : 's'}` : '',
          toolCount > 0 ? `${toolCount} tool${toolCount === 1 ? '' : 's'}` : '',
          skills.length > 0 ? `${skills.length} Skill${skills.length === 1 ? '' : 's'}` : '',
        ].filter(Boolean).join(', ')} in the content you can access. Start with these strong matches:`
      : `根据你当前可见的内容，我找到了${parts.join('、')}。优先看看下面这些匹配度较高的能力：`,
    suggestions,
    followUps,
  }
}
