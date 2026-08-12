import { useTranslation } from 'react-i18next'
import { RESOURCE_CATEGORY_OPTIONS, resourceCategoryLabel, type ResourceCategoryCode } from '@/shared/lib/resource-category'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/shared/ui/select'

const ALL_CATEGORY_VALUE = '__all_categories__'

interface ResourceCategorySelectProps {
  value?: ResourceCategoryCode
  onChange: (value: ResourceCategoryCode | undefined) => void
  id?: string
  className?: string
  triggerPrefix?: string
}

/** The one shared scenario filter for Skills, Agents, and Tools. */
export function ResourceCategorySelect({ value, onChange, id, className, triggerPrefix }: ResourceCategorySelectProps) {
  const { t } = useTranslation()
  const label = value ? resourceCategoryLabel(t, value) : t('resourceCategory.allOption')
  return (
    <Select value={value ?? ALL_CATEGORY_VALUE} onValueChange={(next) => onChange(next === ALL_CATEGORY_VALUE ? undefined : next as ResourceCategoryCode)}>
      <SelectTrigger id={id} className={className}>
        <span>{triggerPrefix ? `${triggerPrefix}${label}` : label}</span>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL_CATEGORY_VALUE}>{t('resourceCategory.allOption')}</SelectItem>
        {RESOURCE_CATEGORY_OPTIONS.map((option) => (
          <SelectItem key={option.code} value={option.code}>{resourceCategoryLabel(t, option.code)}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
