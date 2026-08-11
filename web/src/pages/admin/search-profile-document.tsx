import { Link, useParams } from '@tanstack/react-router'
import { ArrowLeft, RefreshCw } from 'lucide-react'
import { useRegenerateResourceSearchDocument, useResourceSearchDocument } from '@/features/admin/use-resource-search-documents'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

interface Capability {
  value: string
  evidence?: string
  confidence?: number
}

function parseArray(value: string): string[] {
  try {
    const parsed: unknown = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

function parseCapabilities(value: string): Capability[] {
  try {
    const parsed: unknown = JSON.parse(value || '[]')
    return Array.isArray(parsed)
      ? parsed.filter((item): item is Capability => typeof item === 'object' && item !== null && 'value' in item)
      : []
  } catch {
    return []
  }
}

function TextList({ title, values }: { title: string; values: string[] }) {
  if (values.length === 0) return null
  return <section><h2 className="mb-3 text-lg font-semibold">{title}</h2><ul className="list-disc space-y-1 pl-5 text-sm leading-6 text-foreground/85">{values.map(value => <li key={value}>{value}</li>)}</ul></section>
}

/** Full administrator-facing document view for one generated search profile. */
export function AdminSearchProfileDocumentPage() {
  const { resourceType, resourceId } = useParams({ from: '/admin/search-profiles/$resourceType/$resourceId' })
  const document = useResourceSearchDocument(resourceType, Number(resourceId))
  const regenerate = useRegenerateResourceSearchDocument()

  if (document.isLoading) return <div className="mx-auto max-w-4xl px-5 py-8 text-muted-foreground">正在加载搜索画像文档…</div>
  if (!document.data) return <div className="mx-auto max-w-4xl px-5 py-8 text-muted-foreground">未找到该搜索画像文档。</div>

  const profile = document.data
  const capabilities = parseCapabilities(profile.capabilitiesJson)
  return <main className="mx-auto max-w-4xl space-y-6 px-5 py-8 md:px-10">
    <Link to="/admin/search-profiles" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"><ArrowLeft className="h-4 w-4" />返回搜索画像管理</Link>
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm text-muted-foreground">{profile.resourceType} · {profile.accessMode} · {profile.generationStatus}</p><CardTitle className="mt-2 text-3xl">{profile.title}</CardTitle><p className="mt-2 text-sm text-muted-foreground">{profile.summary}</p></div><Button variant="outline" disabled={regenerate.isPending} onClick={() => regenerate.mutate({ resourceType, resourceId: Number(resourceId) })}><RefreshCw className="mr-1.5 h-4 w-4" />重新生成</Button></div>
      </CardHeader>
      <CardContent><div className="flex flex-wrap gap-x-8 gap-y-2 text-sm"><span><strong>公司相关度：</strong>{profile.companyRelevance}</span><span><strong>搜索状态：</strong>{profile.searchEnabled ? '已启用' : '已停用'}</span></div></CardContent>
    </Card>
    <Card><CardHeader><CardTitle>AI 搜索画像</CardTitle></CardHeader><CardContent className="space-y-7"><p className="whitespace-pre-wrap text-sm leading-7 text-foreground/85">{profile.profileText || '尚未生成搜索画像。'}</p><section><h2 className="mb-3 text-lg font-semibold">能力与证据</h2><div className="space-y-4">{capabilities.length === 0 ? <p className="text-sm text-muted-foreground">尚未提炼能力。</p> : capabilities.map(capability => <div key={capability.value} className="rounded-lg border p-4"><div className="font-medium">{capability.value}</div>{capability.evidence ? <p className="mt-2 text-sm leading-6 text-muted-foreground">证据：{capability.evidence}</p> : null}{capability.confidence !== undefined ? <p className="mt-1 text-xs text-muted-foreground">置信度：{Math.round(capability.confidence * 100)}%</p> : null}</div>)}</div></section><TextList title="适用场景" values={parseArray(profile.scenariosJson)} /><TextList title="输入" values={parseArray(profile.inputsJson)} /><TextList title="输出" values={parseArray(profile.outputsJson)} /><TextList title="搜索词" values={parseArray(profile.searchTermsJson)} /></CardContent></Card>
    <Card><CardHeader><CardTitle>原始基础文档</CardTitle></CardHeader><CardContent>{profile.rawDocumentation ? <MarkdownRenderer content={profile.rawDocumentation} /> : <p className="text-sm text-muted-foreground">发布时未提供可用于搜索的原始文档。</p>}</CardContent></Card>
  </main>
}
