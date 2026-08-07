import { useRef, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { CatalogResourceDetail, CatalogResourceKind, CatalogResourceRequest, CatalogVisibilityScope } from '@/api/types'
import { fetchJson, getCsrfHeaders, namespaceApi } from '@/api/client'
import { CATALOG_RESOURCE_KINDS, catalogKindLabel } from '@/entities/catalog-resource/catalog-resource-kind'
import { useCreateCatalogResource, useUpdateCatalogResource } from './use-catalog-queries'
import { usePublishSkill, useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useSkillRepositories } from '@/shared/hooks/use-skill-repositories'
import { resolveDefaultRepositorySlug } from '@/shared/lib/repository-display'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'

interface CatalogResourceFormProps {
  onCreated: (slug: string) => void
  initialKind?: CatalogResourceKind
  resource?: CatalogResourceDetail
}

type OnlineToolHostingMode = 'MANAGED_STATIC' | 'EXTERNAL'

const FIELD_CLASS = 'mt-2 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm'

function appendGeneratedContent(existing: string, generated: string, separator: string): string {
  const current = existing.trim()
  const draft = generated.trim()
  if (!current) return draft
  if (!draft) return current
  return `${current}${separator}${draft}`
}

export function CatalogResourceForm({ onCreated, initialKind, resource }: CatalogResourceFormProps) {
  const { t } = useTranslation()
  const createMutation = useCreateCatalogResource()
  const updateMutation = useUpdateCatalogResource()
  const { data: departments = [] } = useQuery({ queryKey: ['namespaces', 'mine'], queryFn: () => namespaceApi.listMine() })
  const [kind, setKind] = useState<CatalogResourceKind>(() => resource?.kind ?? initialKind ?? 'ONLINE_TOOL')
  const [visibility, setVisibility] = useState<CatalogVisibilityScope>(() => resource?.visibilityScope ?? 'COMPANY')
  const [artifact, setArtifact] = useState<File>()
  const [hostingMode, setHostingMode] = useState<OnlineToolHostingMode>(() => {
    if (!resource) {
      return (initialKind ?? 'ONLINE_TOOL') === 'ONLINE_TOOL' ? 'MANAGED_STATIC' : 'EXTERNAL'
    }
    if (resource?.kind === 'ONLINE_TOOL') {
      const hasStableManagedUrl = Boolean(resource.accessUrl?.includes(`/apps/${resource.slug}/`))
      return resource.artifactAvailable && (!resource.accessUrl || hasStableManagedUrl)
        ? 'MANAGED_STATIC'
        : 'EXTERNAL'
    }
    return 'EXTERNAL'
  })
  const [selectedDepartments, setSelectedDepartments] = useState<number[]>(() => resource?.visibleDepartments?.flatMap((item) => item.id === undefined ? [] : [item.id]) ?? [])
  const [documentation, setDocumentation] = useState(() => resource?.documentation ?? '')
  const [summary, setSummary] = useState(() => resource?.summary ?? '')
  const [isExtractingDocument, setIsExtractingDocument] = useState(false)
  const [isGeneratingDocumentation, setIsGeneratingDocumentation] = useState(false)
  const [documentationGenerationError, setDocumentationGenerationError] = useState('')
  const [examplePrompts, setExamplePrompts] = useState(() => resource?.agentExamplePrompts?.join('\n') ?? '')
  const [selectedSkillIds, setSelectedSkillIds] = useState<number[]>(() => resource?.relatedSkills?.flatMap((skill) => skill.id === undefined ? [] : [skill.id]) ?? [])
  const [skillQuery, setSkillQuery] = useState('')
  const [localSkillFile, setLocalSkillFile] = useState<File>()
  const [skillRepository, setSkillRepository] = useState('')
  const [skillVisibility, setSkillVisibility] = useState('WAREHOUSE')
  const [localSkillError, setLocalSkillError] = useState('')
  const isAgent = kind === 'AGENT'
  const isOnlineTool = kind === 'ONLINE_TOOL'
  const isManagedStatic = isOnlineTool && hostingMode === 'MANAGED_STATIC'
  const supportsArtifact = !isAgent && (!isOnlineTool || isManagedStatic)
  const isExistingManagedStatic = resource?.kind === 'ONLINE_TOOL'
    && resource.artifactAvailable
    && (!resource.accessUrl || resource.accessUrl.includes(`/apps/${resource.slug}/`))
  const { data: skills } = useSearchSkills({ q: skillQuery || undefined, sort: 'newest', page: 0, size: 12 })
  const { data: repositories = [] } = useSkillRepositories()
  const publishSkillMutation = usePublishSkill()
  const formRef = useRef<HTMLFormElement>(null)
  const isEditing = Boolean(resource)

  const splitLines = (value: string) => value.split('\n').map((item) => item.trim()).filter(Boolean)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const split = (value: FormDataEntryValue | null) => String(value ?? '').split(',').map((item) => item.trim()).filter(Boolean)
    const primaryDepartmentValue = String(form.get('primaryDepartmentId') ?? '')
    const feishuAppId = String(form.get('feishuAppId') ?? '').trim()
    const requestedVersion = String(form.get('version') ?? '').trim()
    const publishRequested = !isEditing && form.get('publish') === 'on'
    setLocalSkillError('')
    let relatedSkillIds = selectedSkillIds
    if (isAgent && localSkillFile) {
      const repository = skillRepository || resolveDefaultRepositorySlug(repositories)
      if (!repository) {
        setLocalSkillError('请选择上传 Skill 的归属空间。')
        return
      }
      const publishedSkill = await publishSkillMutation.mutateAsync({
        namespace: repository,
        file: localSkillFile,
        visibility: skillVisibility,
      })
      relatedSkillIds = [...new Set([...relatedSkillIds, publishedSkill.skillId])]
    }

    const request: CatalogResourceRequest = {
        slug: isAgent ? '' : String(form.get('slug') ?? '').trim(),
        name: String(form.get('name') ?? '').trim(),
        summary: summary.trim(),
        kind,
        icon: String(form.get('icon') ?? '').trim() || undefined,
        accessUrl: isAgent
          ? `https://applink.feishu.cn/client/bot/open?appId=${encodeURIComponent(feishuAppId)}`
          : isManagedStatic
            ? isExistingManagedStatic ? resource.accessUrl : undefined
            : String(form.get('accessUrl') ?? '').trim() || undefined,
        documentation: documentation.trim(),
        version: isManagedStatic && isEditing && resource?.status === 'PUBLISHED'
          ? resource.version
          : requestedVersion || undefined,
        agentUsageBoundary: undefined,
        agentInputGuide: undefined,
        agentOutputGuide: undefined,
        agentSupportContact: undefined,
        agentExamplePrompts: isAgent ? splitLines(examplePrompts) : [],
        primaryDepartmentId: primaryDepartmentValue ? Number(primaryDepartmentValue) : undefined,
        maintenanceStatus: resource?.maintenanceStatus ?? 'ACTIVE',
        visibilityScope: visibility,
        visibleDepartmentIds: visibility === 'DEPARTMENTS' ? selectedDepartments : [],
        scenarios: split(form.get('scenarios')),
        tags: split(form.get('tags')),
        relatedResourceIds: [],
        relatedSkillIds,
        publish: publishRequested && !isManagedStatic,
      }
    const result = resource
      ? await updateMutation.mutateAsync({
          slug: resource.slug,
          request,
          artifact,
          publishVersion: isManagedStatic && artifact && resource.status === 'PUBLISHED'
            ? requestedVersion
            : undefined,
        })
      : await createMutation.mutateAsync({
          request,
          artifact,
          publishVersion: isManagedStatic && publishRequested ? requestedVersion : undefined,
        })
    onCreated(result.slug)
  }

  async function extractDocument(file?: File) {
    if (!file) return
    setIsExtractingDocument(true)
    try {
      const body = new FormData()
      body.append('file', file)
      const extracted = await fetchJson<string>('/api/web/catalog/document-text', { method: 'POST', headers: getCsrfHeaders(), body })
      setDocumentation(extracted)
    } finally {
      setIsExtractingDocument(false)
    }
  }

  async function generateDocumentation() {
    const form = formRef.current
    if (!form) return
    if (!isAgent) {
      if (!artifact) {
        setDocumentationGenerationError('请先选择工具 ZIP，再点击 AI 解析并生成说明。')
        return
      }
      setDocumentationGenerationError('')
      setIsGeneratingDocumentation(true)
      try {
        const body = new FormData()
        body.append('file', artifact)
        const draft = await fetchJson<{ summary: string; documentation: string }>('/api/web/catalog/tool-documentation-draft', {
          method: 'POST',
          headers: getCsrfHeaders(),
          body,
        })
        setSummary((current) => appendGeneratedContent(current, draft.summary, '\n'))
        setDocumentation((current) => appendGeneratedContent(current, draft.documentation, '\n\n'))
      } catch (error) {
        setDocumentationGenerationError(error instanceof Error ? error.message : 'AI 使用说明生成失败，请稍后重试。')
      } finally {
        setIsGeneratingDocumentation(false)
      }
      return
    }
    const values = new FormData(form)
    const name = String(values.get('name') ?? '').trim()
    const summary = String(values.get('summary') ?? '').trim()
    const scenarios = String(values.get('scenarios') ?? '').split(',').map((item) => item.trim()).filter(Boolean)
    if (!name || !summary) {
      setDocumentationGenerationError('请先填写 Agent 名称和简介，再生成使用说明。')
      return
    }
    setDocumentationGenerationError('')
    setIsGeneratingDocumentation(true)
    try {
      const draft = await fetchJson<string>('/api/web/catalog/agent-documentation-draft', {
        method: 'POST',
        headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ name, summary, scenarios, existingDocumentation: documentation }),
      })
      setDocumentation(draft)
    } catch (error) {
      setDocumentationGenerationError(error instanceof Error ? error.message : 'AI 使用说明生成失败，请稍后重试。')
    } finally {
      setIsGeneratingDocumentation(false)
    }
  }

  return (
    <form ref={formRef} onSubmit={(event) => void submit(event)} className="space-y-8">
      <section className="grid gap-6 rounded-2xl border bg-card p-6 md:grid-cols-2">
        <div className="md:col-span-2">
          <h2 className="text-xl font-semibold">基础信息</h2>
          <p className="mt-1 text-sm text-muted-foreground">同一项能力只维护一个主条目。</p>
        </div>
        <div><Label htmlFor="name">名称 *</Label><Input className="mt-2" id="name" name="name" required maxLength={160} defaultValue={resource?.name} /></div>
        {!isAgent ? <div><Label htmlFor="slug">唯一标识 {isEditing ? '（不可修改）' : '*'}</Label><Input className="mt-2" id="slug" name="slug" required maxLength={96} pattern="[a-z0-9][a-z0-9-]*" placeholder="spine-preview" defaultValue={resource?.slug} readOnly={isEditing} /></div> : null}
        {!isAgent || !initialKind ? <div>
          <Label htmlFor="kind">内容类型 *</Label>
          <select id="kind" value={kind} onChange={(event) => setKind(event.target.value as CatalogResourceKind)} className={FIELD_CLASS} disabled={isEditing}>
            {CATALOG_RESOURCE_KINDS.map((item) => <option key={item} value={item}>{catalogKindLabel(item)}</option>)}
          </select>
        </div> : <div><Label>内容类型</Label><div className={`${FIELD_CLASS} text-muted-foreground`}>Agent</div></div>}
        {isOnlineTool ? <div>
          <Label htmlFor="hostingMode">{t('catalogPublish.hostingMode')}</Label>
          <select
            id="hostingMode"
            value={hostingMode}
            onChange={(event) => setHostingMode(event.target.value as OnlineToolHostingMode)}
            disabled={isExistingManagedStatic}
            className={FIELD_CLASS}
          >
            <option value="MANAGED_STATIC">{t('catalogPublish.managedStatic')}</option>
            <option value="EXTERNAL">{t('catalogPublish.externalLink')}</option>
          </select>
          <p className="mt-2 text-xs text-muted-foreground">
            {isManagedStatic ? t('catalogPublish.managedStaticHint') : t('catalogPublish.externalLinkHint')}
          </p>
        </div> : null}
        <div><Label htmlFor="icon">图标</Label><Input className="mt-2" id="icon" name="icon" placeholder="可填写 Emoji 或图片地址" defaultValue={resource?.icon ?? ''} /></div>
        <div className="md:col-span-2"><Label htmlFor="summary">简介 *</Label><Textarea className="mt-2" id="summary" name="summary" required maxLength={1200} rows={3} value={summary} onChange={(event) => setSummary(event.target.value)} /></div>
        <div>
          <Label htmlFor={isAgent ? 'feishuAppId' : 'accessUrl'}>{isAgent ? '飞书机器人 App ID *' : '访问入口'}</Label>
          <Input
            className="mt-2"
            id={isAgent ? 'feishuAppId' : 'accessUrl'}
            name={isAgent ? 'feishuAppId' : 'accessUrl'}
            type={isAgent ? 'text' : 'url'}
            required={isAgent || (isOnlineTool && !isManagedStatic)}
            disabled={isManagedStatic}
            pattern={isAgent ? 'cli_[A-Za-z0-9]+' : undefined}
            placeholder={isAgent ? 'cli_xxxxxxxxxxxxxxxx' : isManagedStatic ? t('catalogPublish.accessUrlGenerated') : 'https://...'}
            defaultValue={isAgent ? new URL(resource?.accessUrl ?? 'https://applink.feishu.cn').searchParams.get('appId') ?? '' : resource?.accessUrl ?? ''}
          />
          {isAgent ? <p className="mt-2 text-xs text-muted-foreground">在飞书开发者后台「凭证与基础信息」中获取。系统会自动生成“立即使用”的机器人会话链接。</p> : null}
          {isManagedStatic ? <p className="mt-2 text-xs text-muted-foreground">{t('catalogPublish.accessUrlHint')}</p> : null}
        </div>
        {!isAgent ? <div><Label htmlFor="version">版本{isManagedStatic ? ' *' : ''}</Label><Input className="mt-2" id="version" name="version" placeholder="1.0.0" required={isManagedStatic} readOnly={isManagedStatic && isEditing && !artifact} defaultValue={resource?.version ?? ''} />{isManagedStatic && isEditing && !artifact ? <p className="mt-2 text-xs text-muted-foreground">{t('catalogPublish.versionWithArtifactHint')}</p> : null}</div> : <div><Label>当前发布方式</Label><div className={`${FIELD_CLASS} text-muted-foreground`}>飞书机器人</div></div>}
        <div><Label htmlFor="scenarios">适用场景 {isAgent ? '*' : ''}</Label><Input className="mt-2" id="scenarios" name="scenarios" required={isAgent} placeholder="研发提效, 美术资产处理" defaultValue={resource?.scenarios?.join(', ') ?? ''} /></div>
        <div><Label htmlFor="tags">标签</Label><Input className="mt-2" id="tags" name="tags" placeholder="预览, 内部工具" defaultValue={resource?.tags?.join(', ') ?? ''} /></div>
      </section>

      {isAgent ? <section className="space-y-5 rounded-2xl border bg-card p-6">
        <div><h2 className="text-xl font-semibold">关联 Skill <span className="text-sm font-normal text-muted-foreground">（可选）</span></h2><p className="mt-1 text-sm text-muted-foreground">选择已发布的 Skill，或上传本地 Skill 包。上传成功后会自动关联到此 Agent。</p></div>
        <div className="space-y-3">
          <Label htmlFor="relatedSkillSearch">从 Skill 中心选择</Label>
          <Input id="relatedSkillSearch" value={skillQuery} onChange={(event) => setSkillQuery(event.target.value)} placeholder="搜索已发布的 Skill" />
          <div className="grid gap-2 md:grid-cols-2">
            {(skills?.items ?? []).map((skill) => (
              <label key={skill.id} className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm">
                <input type="checkbox" checked={selectedSkillIds.includes(skill.id)} onChange={(event) => setSelectedSkillIds((current) => event.target.checked ? [...current, skill.id] : current.filter((id) => id !== skill.id))} />
                <span><span className="font-medium">{skill.displayName}</span><span className="block text-xs text-muted-foreground">@{skill.namespace}/{skill.slug}</span></span>
              </label>
            ))}
          </div>
          {skills && skills.items.length === 0 ? <p className="text-sm text-muted-foreground">没有找到可关联的 Skill。</p> : null}
        </div>
        <div className="rounded-xl border border-dashed p-4">
          <Label htmlFor="localSkill">上传本地 Skill 包</Label>
          <Input id="localSkill" className="mt-2" type="file" accept=".zip,application/zip" onChange={(event) => setLocalSkillFile(event.target.files?.[0])} />
          <p className="mt-1 text-xs text-muted-foreground">ZIP 内须包含 SKILL.md；会使用平台原有的安全校验和发布流程。</p>
          {localSkillFile ? <div className="mt-4 grid gap-3 md:grid-cols-2"><div><Label htmlFor="skillRepository">归属空间 *</Label><select id="skillRepository" className={FIELD_CLASS} value={skillRepository || resolveDefaultRepositorySlug(repositories)} onChange={(event) => setSkillRepository(event.target.value)}><option value="">请选择</option>{repositories.map((repository) => <option key={repository.slug} value={repository.slug}>{repository.displayName}</option>)}</select></div><div><Label htmlFor="skillVisibility">可见范围</Label><select id="skillVisibility" className={FIELD_CLASS} value={skillVisibility} onChange={(event) => setSkillVisibility(event.target.value)}><option value="WAREHOUSE">空间内可见</option><option value="PRIVATE">仅自己可见</option></select></div></div> : null}
          {localSkillError ? <p className="mt-2 text-sm text-destructive">{localSkillError}</p> : null}
        </div>
      </section> : null}

      <section className="space-y-5 rounded-2xl border bg-card p-6">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-xl font-semibold">使用说明 *</h2><p className="mt-1 text-sm text-muted-foreground">{isAgent ? '这是 Agent 的完整使用说明，请在这里写清输入要求、能力边界和反馈方式；支持 Markdown。' : '说明这项工具如何使用；支持 Markdown。可根据上传的归档生成草稿，再自行检查和编辑。'}</p></div>{isAgent || supportsArtifact ? <Button type="button" variant="outline" size="sm" disabled={isGeneratingDocumentation} onClick={() => void generateDocumentation()}>{isGeneratingDocumentation ? '正在生成...' : isAgent ? 'AI 生成草稿' : 'AI 解析并生成说明'}</Button> : null}</div>
        <Textarea id="documentation" name="documentation" required rows={14} value={documentation} onChange={(event) => setDocumentation(event.target.value)} placeholder={'# 快速开始\n\n说明如何访问、安装或配置这项能力。'} />
        {isAgent ? <><div className="rounded-xl border border-dashed p-4"><Label htmlFor="documentationFile">上传并解析文档</Label><Input id="documentationFile" className="mt-2" type="file" accept=".docx,.md,.txt" disabled={isExtractingDocument} onChange={(event) => void extractDocument(event.target.files?.[0])} /><p className="mt-2 text-xs text-muted-foreground">支持 Word（.docx）、Markdown 和文本文件；解析后请检查并编辑内容。{isExtractingDocument ? ' 正在解析…' : ''}</p></div><div><Label htmlFor="agentExamplePrompts">示例提问 <span className="text-muted-foreground">（可选，每行一条）</span></Label><Textarea id="agentExamplePrompts" className="mt-2" rows={3} value={examplePrompts} onChange={(event) => setExamplePrompts(event.target.value)} placeholder={'请把下面的会议纪要整理成待办事项\n根据这段项目背景给我下一步建议'} /><p className="mt-1 text-xs text-muted-foreground">仅用于让用户快速开始对话；完整规则仍以使用说明为准。</p></div></> : null}
        {documentationGenerationError ? <p className="text-sm text-destructive">{documentationGenerationError}</p> : null}
        {supportsArtifact ? <div>
          <Label htmlFor="artifact">{isManagedStatic ? t('catalogPublish.staticArtifact') : '安装包（可选）'}</Label>
          <Input id="artifact" className="mt-2" type="file" accept=".zip,application/zip" required={isManagedStatic && !resource?.artifactAvailable} onChange={(event) => setArtifact(event.target.files?.[0])} />
          <p className="mt-2 text-xs text-muted-foreground">{isManagedStatic ? `${t('catalogPublish.staticArtifactHint')} 选择文件后可点击上方“AI 解析并生成说明”。` : '插件、模板和资源包可上传 ZIP，最大 100MB。选择文件后可点击上方“AI 解析并生成说明”。'}</p>
          {isManagedStatic && resource?.artifactFilename ? <p className="mt-2 text-xs text-muted-foreground">{t('catalogPublish.currentArtifact', { filename: resource.artifactFilename })}</p> : null}
        </div> : null}
      </section>

      <section className="grid gap-6 rounded-2xl border bg-card p-6 md:grid-cols-2">
        <div className="md:col-span-2"><h2 className="text-xl font-semibold">归属与可见范围</h2></div>
        <div>
          <Label htmlFor="primaryDepartmentId">所属部门</Label>
          <select id="primaryDepartmentId" name="primaryDepartmentId" className={FIELD_CLASS} defaultValue={resource?.department?.id ?? ''}>
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
        {isEditing ? <p className="text-sm text-muted-foreground">{isManagedStatic && artifact && resource?.status === 'PUBLISHED' ? t('catalogPublish.updateDeployHint') : '保存修改不会改变当前的发布状态。'}</p> : <label className="flex items-center gap-3 text-sm"><input name="publish" type="checkbox" defaultChecked /> {isManagedStatic ? t('catalogPublish.publishAndDeploy') : '填写完成后直接发布'}</label>}
        <Button type="submit" size="lg" disabled={createMutation.isPending || updateMutation.isPending || publishSkillMutation.isPending || (visibility === 'DEPARTMENTS' && selectedDepartments.length === 0)}>
          {createMutation.isPending || updateMutation.isPending || publishSkillMutation.isPending ? t('catalogPublish.deploying') : isEditing && isManagedStatic && artifact && resource?.status === 'PUBLISHED' ? t('catalogPublish.saveAndDeploy') : isEditing ? '保存修改' : isManagedStatic ? t('catalogPublish.saveResource') : '保存资源'}
        </Button>
      </div>
      {createMutation.isError || updateMutation.isError ? <p className="text-sm text-destructive">{createMutation.error?.message ?? updateMutation.error?.message}</p> : null}
    </form>
  )
}
