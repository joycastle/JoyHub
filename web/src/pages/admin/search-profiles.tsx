import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useRegenerateResourceSearchDocument, useResourceSearchDocuments } from '@/features/admin/use-resource-search-documents'
import { Button } from '@/shared/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

export function AdminSearchProfilesPage() {
  const [resourceType, setResourceType] = useState('ALL')
  const [generationStatus, setGenerationStatus] = useState('ALL')
  const documents = useResourceSearchDocuments({
    resourceType: resourceType === 'ALL' ? undefined : resourceType,
    generationStatus: generationStatus === 'ALL' ? undefined : generationStatus,
    page: 0,
    size: 100,
  })
  const regenerate = useRegenerateResourceSearchDocument()
  return <div className="mx-auto max-w-7xl space-y-6 px-5 py-8 md:px-10">
    <div>
      <h1 className="text-3xl font-bold">搜索画像管理</h1>
      <p className="mt-2 text-muted-foreground">查看统一搜索文档、AI 生成状态和证据；重生成不会影响资源发布或基础搜索。</p>
    </div>
    <div className="flex flex-wrap gap-3">
      <Select value={resourceType} onValueChange={setResourceType}><SelectTrigger className="w-40"><span>类型：{resourceType}</span></SelectTrigger><SelectContent>{['ALL', 'SKILL', 'AGENT', 'TOOL'].map(value => <SelectItem key={value} value={value}>{value}</SelectItem>)}</SelectContent></Select>
      <Select value={generationStatus} onValueChange={setGenerationStatus}><SelectTrigger className="w-44"><span>状态：{generationStatus}</span></SelectTrigger><SelectContent>{['ALL', 'BASIC', 'PENDING', 'READY', 'FAILED'].map(value => <SelectItem key={value} value={value}>{value}</SelectItem>)}</SelectContent></Select>
    </div>
    {documents.isLoading ? <p className="text-muted-foreground">正在加载…</p> : null}
    <div className="overflow-x-auto rounded-lg border bg-card"><Table><TableHeader><TableRow><TableHead>资源</TableHead><TableHead>类型/访问</TableHead><TableHead>生成状态</TableHead><TableHead>相关度</TableHead><TableHead>搜索画像</TableHead><TableHead>操作</TableHead></TableRow></TableHeader><TableBody>
      {(documents.data?.items ?? []).map(document => <TableRow key={`${document.resourceType}:${document.resourceId}`}><TableCell><Link to="/admin/search-profiles/$resourceType/$resourceId" params={{ resourceType: document.resourceType, resourceId: String(document.resourceId) }} className="font-medium text-primary hover:underline">{document.title}</Link><div className="text-xs text-muted-foreground">{document.slug}</div></TableCell><TableCell>{document.resourceType} / {document.accessMode}</TableCell><TableCell>{document.generationStatus}</TableCell><TableCell>{document.companyRelevance}</TableCell><TableCell className="max-w-md whitespace-normal text-sm text-muted-foreground">{document.profileText || '尚未生成'}</TableCell><TableCell><div className="flex items-center gap-3 whitespace-nowrap"><Link to="/admin/search-profiles/$resourceType/$resourceId" params={{ resourceType: document.resourceType, resourceId: String(document.resourceId) }} className="inline-flex h-9 items-center text-sm font-medium text-primary hover:underline">查看文档</Link><Button size="sm" variant="outline" disabled={regenerate.isPending} onClick={() => regenerate.mutate({ resourceType: document.resourceType, resourceId: document.resourceId })}><RefreshCw className="mr-1.5 h-3.5 w-3.5" />重新生成</Button></div></TableCell></TableRow>)}
    </TableBody></Table></div>
  </div>
}
