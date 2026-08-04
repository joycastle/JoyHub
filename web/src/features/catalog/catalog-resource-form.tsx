import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { CatalogResourceKind, CatalogVisibilityScope } from '@/api/types'
import { namespaceApi } from '@/api/client'
import { CATALOG_RESOURCE_KINDS, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCreateCatalogResource } from './use-catalog-queries'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'

interface CatalogResourceFormProps {
  onCreated: (slug: string) => void
  initialKind?: CatalogResourceKind
}

const FIELD_CLASS = 'mt-2 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm'

export function CatalogResourceForm({ onCreated, initialKind }: CatalogResourceFormProps) {
  const createMutation = useCreateCatalogResource()
  const { data: departments = [] } = useQuery({ queryKey: ['namespaces', 'mine'], queryFn: () => namespaceApi.listMine() })
  const [kind, setKind] = useState<CatalogResourceKind>(() => initialKind ?? 'ONLINE_TOOL')
  const [visibility, setVisibility] = useState<CatalogVisibilityScope>('COMPANY')
  const [artifact, setArtifact] = useState<File>()
  const [selectedDepartments, setSelectedDepartments] = useState<number[]>([])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const split = (value: FormDataEntryValue | null) => String(value ?? '').split(',').map((item) => item.trim()).filter(Boolean)
    const primaryDepartmentValue = String(form.get('primaryDepartmentId') ?? '')
    const result = await createMutation.mutateAsync({
      request: {
        slug: String(form.get('slug') ?? '').trim(),
        name: String(form.get('name') ?? '').trim(),
        summary: String(form.get('summary') ?? '').trim(),
        kind,
        icon: String(form.get('icon') ?? '').trim() || undefined,
        accessUrl: String(form.get('accessUrl') ?? '').trim() || undefined,
        documentation: String(form.get('documentation') ?? '').trim(),
        version: String(form.get('version') ?? '').trim() || undefined,
        primaryDepartmentId: primaryDepartmentValue ? Number(primaryDepartmentValue) : undefined,
        maintenanceStatus: 'ACTIVE',
        visibilityScope: visibility,
        visibleDepartmentIds: visibility === 'DEPARTMENTS' ? selectedDepartments : [],
        scenarios: split(form.get('scenarios')),
        tags: split(form.get('tags')),
        relatedResourceIds: [],
        relatedSkillIds: [],
        publish: form.get('publish') === 'on',
      },
      artifact,
    })
    onCreated(result.slug)
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="space-y-8">
      <section className="grid gap-6 rounded-2xl border bg-card p-6 md:grid-cols-2">
        <div className="md:col-span-2">
          <h2 className="text-xl font-semibold">基础信息</h2>
          <p className="mt-1 text-sm text-muted-foreground">同一项能力只维护一个主条目。</p>
        </div>
        <div><Label htmlFor="name">名称 *</Label><Input className="mt-2" id="name" name="name" required maxLength={160} /></div>
        <div><Label htmlFor="slug">唯一标识 *</Label><Input className="mt-2" id="slug" name="slug" required maxLength={96} pattern="[a-z0-9][a-z0-9-]*" placeholder="spine-preview" /></div>
        <div>
          <Label htmlFor="kind">内容类型 *</Label>
          <select id="kind" value={kind} onChange={(event) => setKind(event.target.value as CatalogResourceKind)} className={FIELD_CLASS}>
            {CATALOG_RESOURCE_KINDS.map((item) => <option key={item} value={item}>{catalogKindLabel(item)}</option>)}
          </select>
        </div>
        <div><Label htmlFor="icon">图标</Label><Input className="mt-2" id="icon" name="icon" placeholder="可填写 Emoji 或图片地址" /></div>
        <div className="md:col-span-2"><Label htmlFor="summary">简介 *</Label><Textarea className="mt-2" id="summary" name="summary" required maxLength={1200} rows={3} /></div>
        <div><Label htmlFor="accessUrl">访问入口</Label><Input className="mt-2" id="accessUrl" name="accessUrl" type="url" placeholder="https://..." /></div>
        <div><Label htmlFor="version">版本</Label><Input className="mt-2" id="version" name="version" placeholder="1.0.0" /></div>
        <div><Label htmlFor="scenarios">适用场景</Label><Input className="mt-2" id="scenarios" name="scenarios" placeholder="研发提效, 美术资产处理" /></div>
        <div><Label htmlFor="tags">标签</Label><Input className="mt-2" id="tags" name="tags" placeholder="预览, 内部工具" /></div>
      </section>

      <section className="space-y-5 rounded-2xl border bg-card p-6">
        <div><h2 className="text-xl font-semibold">对应文档 *</h2><p className="mt-1 text-sm text-muted-foreground">每个资源只关联这一份文档，可使用 Markdown。</p></div>
        <Textarea id="documentation" name="documentation" required rows={14} placeholder={'# 快速开始\n\n说明如何访问、安装或配置这项能力。'} />
        <div>
          <Label htmlFor="artifact">安装包（可选）</Label>
          <Input id="artifact" className="mt-2" type="file" accept=".zip,application/zip" onChange={(event) => setArtifact(event.target.files?.[0])} />
          <p className="mt-2 text-xs text-muted-foreground">插件、模板和资源包可上传 ZIP，最大 100MB。</p>
        </div>
      </section>

      <section className="grid gap-6 rounded-2xl border bg-card p-6 md:grid-cols-2">
        <div className="md:col-span-2"><h2 className="text-xl font-semibold">归属与可见范围</h2></div>
        <div>
          <Label htmlFor="primaryDepartmentId">所属部门</Label>
          <select id="primaryDepartmentId" name="primaryDepartmentId" className={FIELD_CLASS} defaultValue="">
            <option value="">不指定</option>
            {departments.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}
          </select>
        </div>
        <div>
          <Label htmlFor="visibility">可见范围</Label>
          <select id="visibility" value={visibility} onChange={(event) => setVisibility(event.target.value as CatalogVisibilityScope)} className={FIELD_CLASS}>
            <option value="COMPANY">全公司可见</option>
            <option value="DEPARTMENTS">指定部门可见</option>
          </select>
        </div>
        {visibility === 'DEPARTMENTS' ? (
          <div className="space-y-2 md:col-span-2">
            <Label>可见部门 *</Label>
            <div className="grid gap-2 md:grid-cols-3">
              {departments.map((item) => (
                <label key={item.id} className="flex items-center gap-2 rounded-lg border p-3 text-sm">
                  <input
                    type="checkbox"
                    checked={selectedDepartments.includes(item.id)}
                    onChange={(event) => setSelectedDepartments((current) => event.target.checked ? [...current, item.id] : current.filter((id) => id !== item.id))}
                  />
                  {item.displayName}
                </label>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      <div className="flex items-center justify-between rounded-2xl border bg-card p-5">
        <label className="flex items-center gap-3 text-sm"><input name="publish" type="checkbox" defaultChecked /> 填写完成后直接发布</label>
        <Button type="submit" size="lg" disabled={createMutation.isPending || (visibility === 'DEPARTMENTS' && selectedDepartments.length === 0)}>
          {createMutation.isPending ? '正在提交...' : '保存资源'}
        </Button>
      </div>
      {createMutation.isError ? <p className="text-sm text-destructive">{createMutation.error.message}</p> : null}
    </form>
  )
}
