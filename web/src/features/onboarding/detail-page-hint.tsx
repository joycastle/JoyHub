import { useEffect, useState } from 'react'
import { completeOnboardingTask, hasActiveOnboardingJourney } from './onboarding-progress'
import { FormFeatureTour } from './form-feature-tour'

function hintKey(userId: string) {
  return `joyhub-onboarding-v5:${userId}:detail-hint-dismissed`
}

/** A first-detail-page guide that explains the page in the same order a new user should use it. */
export function DetailPageHint({ userId }: { userId?: string }) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!userId) return
    if (hasActiveOnboardingJourney(userId)) {
      setVisible(false)
      return
    }
    try {
      setVisible(window.localStorage?.getItem(hintKey(userId)) !== 'done')
    } catch {
      setVisible(true)
    }
  }, [userId])

  if (!visible) return null

  return <FormFeatureTour
    label="详情页导览"
    completeLabel="我已了解，继续使用"
    steps={[
      { target: 'detail-header', title: '先确认这项能力能不能用', description: '看名称、类型、状态、可见范围和维护人。状态异常或不在你的可见范围内时，不要继续安装或使用。' },
      { target: 'detail-status', title: '读懂状态和可见范围', description: '“已发布”表示当前可被使用；可见范围说明谁能搜索到它。遇到归档、下线或异常状态，请先联系维护者。' },
      { target: 'detail-tabs', title: '从“使用说明”开始读', description: '这里会写清准备的输入、操作步骤、输出结果和限制。文件与版本页用于确认包内容和更新记录。' },
      { target: 'detail-actions', title: '再执行右侧的使用操作', description: 'Agent 会打开飞书，Skill 可复制安装，工具可直接打开或下载。使用后可收藏，后续在“常用工具”或收藏中找到它。' },
    ]}
    onDismiss={() => {
        try {
          if (userId) window.localStorage?.setItem(hintKey(userId), 'done')
        } catch {
          // The tour can still complete for this page view when storage is unavailable.
        }
        completeOnboardingTask(userId, 'detail')
        setVisible(false)
    }}
  />
}
