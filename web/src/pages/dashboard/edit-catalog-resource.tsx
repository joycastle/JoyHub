import { useNavigate, useParams } from '@tanstack/react-router'
import { CatalogResourceForm } from '@/features/catalog/catalog-resource-form'
import { useCatalogResource } from '@/features/catalog/use-catalog-queries'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

export function EditCatalogResourcePage() {
  const { slug } = useParams({ from: '/dashboard/catalog/$slug/edit' })
  const navigate = useNavigate()
  const { data: resource, isLoading, isError } = useCatalogResource(slug)

  if (isLoading) return <div className={APP_SHELL_PAGE_CLASS_NAME}>正在加载内容...</div>
  if (isError || !resource || !resource.canManage) return <div className={APP_SHELL_PAGE_CLASS_NAME}>无法编辑此内容。</div>

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div>
        <div className="text-sm font-semibold text-primary">JoyHub 内容管理</div>
        <h1 className="mt-2 text-4xl font-bold">编辑 {resource.name}</h1>
        <p className="mt-2 text-muted-foreground">修改会立即更新详情页；已发布内容会保持发布状态。</p>
      </div>
      <CatalogResourceForm
        resource={resource}
        onCreated={(updatedSlug) => navigate({ to: '/catalog/$slug', params: { slug: updatedSlug } })}
      />
    </div>
  )
}
