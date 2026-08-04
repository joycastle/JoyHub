import type { CatalogResourceKind } from '@/api/types'

export const CATALOG_RESOURCE_KINDS: CatalogResourceKind[] = [
  'AGENT',
  'ONLINE_TOOL',
  'PLUGIN',
  'MCP_SERVER',
  'INTERNAL_SERVICE',
  'KNOWLEDGE_BASE',
  'TEMPLATE',
  'RESOURCE_PACK',
]

export function catalogKindLabel(kind: CatalogResourceKind): string {
  return {
    AGENT: 'Agent',
    ONLINE_TOOL: '在线工具',
    PLUGIN: '插件',
    MCP_SERVER: 'MCP / Tool Server',
    INTERNAL_SERVICE: '内部服务',
    KNOWLEDGE_BASE: '知识库',
    TEMPLATE: '模板',
    RESOURCE_PACK: '资源包',
  }[kind]
}

export function catalogKindEmoji(kind: CatalogResourceKind): string {
  return {
    AGENT: '🤖',
    ONLINE_TOOL: '🧰',
    PLUGIN: '🧩',
    MCP_SERVER: '🔌',
    INTERNAL_SERVICE: '⚙️',
    KNOWLEDGE_BASE: '📚',
    TEMPLATE: '📋',
    RESOURCE_PACK: '📦',
  }[kind]
}
