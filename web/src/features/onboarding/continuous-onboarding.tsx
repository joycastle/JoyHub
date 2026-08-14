import { useEffect, useLayoutEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import { useNavigate, useRouterState } from '@tanstack/react-router'
import { CheckCircle2, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { advanceOnboardingJourney, chooseOnboardingGoal, completeOnboardingTask, finishOnboardingJourney, getOnboardingGoal, getOnboardingJourneyStep, getOnboardingTasks, hasCompletedOnboardingJourneyUse, pauseOnboardingJourney, resumeOnboardingJourney, saveOnboardingJourneyPath, subscribeOnboardingGuideOpen, subscribeOnboardingProgress, type OnboardingJourneyStep } from './onboarding-progress'

interface JourneyInstruction {
  target: string
  title: string
  description: string
  action: string
}

function actionGuidance(step: OnboardingJourneyStep, hasSearchResults: boolean) {
  if (step === 'find') {
    return hasSearchResults
      ? '已完成搜索。现在点击“查看搜索结果”，再从结果中挑一张最接近你任务的卡片。'
      : '现在请点击高亮的搜索框，输入“整理会议纪要”等真实任务，然后按 Enter 或点击“搜索”。如果没有结果，换一个更通用的说法再试一次。'
  }
  if (step === 'open') return '现在请直接点击高亮结果中的任意一张卡片。打开详情页后，引导会自动继续。'
  if (step === 'practice') return '先按照高亮区域找到使用说明和示例：准备好对应输入后，下一步再去执行右侧的使用入口。'
  if (step === 'use') return '现在请点击右侧高亮的“在飞书中使用”“复制安装”或“下载”按钮，完成第一次实际使用。'
  return null
}

const INSTRUCTIONS: Record<OnboardingJourneyStep, JourneyInstruction> = {
  start: { target: 'search', title: '用一个真实任务开始', description: '不用先记住 Agent、Skill 和工具的差别。先描述你要完成的工作，JoyHub 会把可用能力找出来。', action: '开始搜索' },
  find: { target: 'search', title: '输入你要完成的工作', description: '例如“整理会议纪要”或“生成周报”。完成一次搜索后，我们一起看结果卡片。', action: '查看搜索结果' },
  agents: { target: 'catalog', title: 'Agent 是“直接用”的助手', description: 'Agent 中心收录可直接在飞书中使用的机器人和自动化助手。卡片上会说明它能做什么；详情页右侧的“在飞书中使用”会打开实际会话。', action: '我已了解 Agent 的用法' },
  tools: { target: 'catalog', title: '工具是“打开或下载”的能力', description: '工具中心用于在线工具、插件和可下载资源。先看卡片的使用方式：有入口就直接打开，有安装包就下载后按说明使用。', action: '我已了解工具的用法' },
  open: { target: 'search-results', title: '从结果中选一个最合适的', description: '先看卡片上的“能做什么”和使用方式：Agent 可直接使用，Skill 可安装，工具可打开或下载。选一张与你的任务最接近的卡片。', action: '等待你打开详情' },
  inspectHeader: { target: 'detail-header', title: '确认它是否适合这次任务', description: '先看名称、类型、维护人和简介。它告诉你“这是什么”，不需要在这里理解所有设置。', action: '下一项：状态和可见范围' },
  inspectStatus: { target: 'detail-status', title: '确认它当前能不能用', description: '“已发布”表示当前可使用；可见范围说明谁能搜索到它。遇到归档、下线或异常状态，请先联系维护人。', action: '下一项：使用说明和版本' },
  inspectTabs: { target: 'detail-tabs', title: '从使用说明开始读', description: '使用说明会写清需要准备的输入、操作步骤、输出和限制。文件与版本页用来确认包内容和更新记录。', action: '下一项：实际使用' },
  practice: { target: 'detail-tabs', title: '先按说明准备，再开始使用', description: '先找到示例输入和准备事项。Skill 看概览中的安装与调用说明；Agent 和工具打开“使用说明”，再执行右侧的使用入口。', action: '去执行使用操作' },
  use: { target: 'detail-actions', title: '完成你的第一次使用', description: '按资源类型选择“在飞书中使用”“复制安装”或“下载”。完成一次真实使用后，本次入门就完成了。', action: '等待你完成使用' },
  useComplete: { target: 'detail-actions', title: '使用入口已打开', description: '你已经打开了真实使用入口。请在飞书、工具页面或终端完成下面提示的第一步；完成后再回到这里结束入门。', action: '完成入门' },
  publishEntry: { target: 'publish', title: '开始发布你的第一项内容', description: '你的成熟方法、机器人或工具都可以沉淀到平台。先在“我的内容”选择发布 Skill 或发布资源。', action: '等待你进入发布页' },
  publishBasics: { target: 'skill-repository', title: '发布第 1 步：确定归属和名称', description: 'Skill 先选择仓库；Agent 或工具先填写名称和类型。它们决定内容归谁维护，以及同事如何找到它。', action: '下一步：适用场景' },
  publishCategory: { target: 'skill-category', title: '发布第 2 步：标记适用场景', description: '选择最贴合的适用场景。它会参与筛选和推荐，所以不要为了曝光选择无关分类。', action: '下一步：说明和文件' },
  publishDocumentation: { target: 'skill-upload', title: '发布第 3 步：写清怎么使用', description: 'Skill 上传带 SKILL.md 的压缩包；Agent 或工具补齐简介、使用说明和真实使用入口。说明应写清输入、操作、输出和限制。', action: '下一步：可见范围' },
  publishScope: { target: 'skill-visibility', title: '发布第 4 步：确定谁能使用', description: '选择部门和可见范围。发布前检查维护人、入口和说明是否都是真实可用的信息。', action: '下一步：提交发布' },
  publishSubmit: { target: 'skill-submit', title: '发布第 5 步：提交并等待结果', description: '确认无误后再提交。内容会进入审核或直接发布；现在无需真的发布，理解这个入口和审核流程后即可完成引导。', action: '我已了解发布流程' },
  manage: { target: 'manage', title: '发布后持续维护', description: '在“我的内容”可以编辑说明和入口、下架失效内容或归档不再维护的内容。这样同事找到的始终是可用版本。', action: '完成入门' },
}

const USE_STEPS: OnboardingJourneyStep[] = ['start', 'find', 'agents', 'tools', 'open', 'inspectHeader', 'inspectStatus', 'inspectTabs', 'practice', 'use', 'useComplete']
const PUBLISH_STEPS: OnboardingJourneyStep[] = ['publishEntry', 'publishBasics', 'publishCategory', 'publishDocumentation', 'publishScope', 'publishSubmit', 'manage']
const DETAIL_STEPS: OnboardingJourneyStep[] = ['inspectHeader', 'inspectStatus', 'inspectTabs', 'practice', 'use', 'useComplete']
const PUBLISH_FORM_STEPS: OnboardingJourneyStep[] = ['publishBasics', 'publishCategory', 'publishDocumentation', 'publishScope', 'publishSubmit']

function isDetailPath(pathname: string) {
  return /^\/catalog\/[^/]+$/.test(pathname) || /^\/space\/[^/]+\/[^/]+$/.test(pathname)
}

/** One continuous, resumable first-run journey that waits for real user actions between pages. */
export function ContinuousOnboarding({ userId }: { userId: string }) {
  const navigate = useNavigate()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const [step, setStep] = useState<OnboardingJourneyStep | null>(() => getOnboardingJourneyStep(userId))
  const [tasks, setTasks] = useState(() => getOnboardingTasks(userId))
  const [hasTarget, setHasTarget] = useState(false)
  const [hasSearchResults, setHasSearchResults] = useState(false)
  const [hasUsedCurrentJourney, setHasUsedCurrentJourney] = useState(() => hasCompletedOnboardingJourneyUse(userId))
  const [resourceType, setResourceType] = useState<'SKILL' | 'AGENT' | 'TOOL' | null>(null)
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null)
  const [dragPosition, setDragPosition] = useState<{ left: number; top: number } | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const dragRef = useRef<{ pointerId: number; offsetX: number; offsetY: number } | null>(null)
  const lastRouteRef = useRef<string | null>(null)
  const instruction = useMemo(() => {
    if (!step) return null
    const resourceTargets: Partial<Record<OnboardingJourneyStep, string>> = {
      publishBasics: 'resource-basics', publishCategory: 'resource-category', publishDocumentation: 'resource-documentation', publishScope: 'resource-scope', publishSubmit: 'resource-submit',
    }
    const baseInstruction = pathname === '/dashboard/catalog/new' && resourceTargets[step]
      ? { ...INSTRUCTIONS[step], target: resourceTargets[step] }
      : INSTRUCTIONS[step]
    return step === 'practice' && resourceType && resourceType !== 'SKILL'
      ? { ...baseInstruction, target: 'usage-tab' }
      : baseInstruction
  }, [pathname, resourceType, step])
  const goal = getOnboardingGoal(userId)
  const journeySteps = goal === 'PUBLISH' ? PUBLISH_STEPS : USE_STEPS
  const isDetail = isDetailPath(pathname)
  const isPublishing = pathname === '/dashboard/publish' || pathname === '/dashboard/catalog/new'

  useEffect(() => {
    const refresh = () => { setStep(getOnboardingJourneyStep(userId)); setTasks(getOnboardingTasks(userId)); setHasUsedCurrentJourney(hasCompletedOnboardingJourneyUse(userId)) }
    return subscribeOnboardingProgress(refresh)
  }, [userId])

  useEffect(() => subscribeOnboardingGuideOpen(() => {
    const resumed = resumeOnboardingJourney(userId)
    setStep(resumed)
    setTasks(getOnboardingTasks(userId))
    setHasUsedCurrentJourney(hasCompletedOnboardingJourneyUse(userId))
  }), [userId])

  useEffect(() => {
    if (step) saveOnboardingJourneyPath(userId, `${pathname}${window.location.search}`)
  }, [pathname, step, userId])

  useEffect(() => {
    if (!step || journeySteps.includes(step)) return
    advanceOnboardingJourney(userId, journeySteps[0])
    navigate({ to: journeySteps[0] === 'publishEntry' ? '/dashboard/resources' : '/' })
  }, [journeySteps, navigate, step, userId])

  // New users often explore through the navigation before following the suggested button.
  // When that happens, continue from the page they deliberately opened instead of leaving a
  // stale instruction pointing at controls from the previous page.
  useEffect(() => {
    if (!step) return
    const routeChanged = lastRouteRef.current !== pathname
    lastRouteRef.current = pathname
    if (!routeChanged) return

    const continueAsUse = (nextStep: OnboardingJourneyStep) => {
      if (goal !== 'USE') chooseOnboardingGoal(userId, 'USE')
      if (step !== nextStep) advanceOnboardingJourney(userId, nextStep)
    }
    const continueAsPublish = (nextStep: OnboardingJourneyStep) => {
      if (goal !== 'PUBLISH') chooseOnboardingGoal(userId, 'PUBLISH')
      if (step !== nextStep) advanceOnboardingJourney(userId, nextStep)
    }

    if (pathname === '/agents') { continueAsUse('agents'); return }
    if (pathname === '/tools') { continueAsUse('tools'); return }
    if (isDetail) {
      if (!DETAIL_STEPS.includes(step)) continueAsUse('inspectHeader')
      return
    }
    if (pathname === '/dashboard/publish' || pathname === '/dashboard/catalog/new') {
      if (!PUBLISH_FORM_STEPS.includes(step)) continueAsPublish('publishBasics')
      return
    }
    if (pathname === '/dashboard/resources') {
      if (step === 'manage' || (step === 'publishSubmit' && tasks.publish)) return
      if (step !== 'publishEntry') continueAsPublish('publishEntry')
      return
    }
    if (pathname === '/' && goal === 'USE' && step === 'start') return
    if (pathname === '/skills' || pathname === '/') {
      continueAsUse('find')
    }
  }, [goal, isDetail, pathname, step, tasks.publish, userId])

  useEffect(() => {
    if ((step === 'find' || step === 'open') && isDetail) { completeOnboardingTask(userId, 'skills'); advanceOnboardingJourney(userId, 'inspectHeader') }
    if (step === 'publishEntry' && isPublishing) advanceOnboardingJourney(userId, 'publishBasics')
    if (step === 'publishSubmit' && pathname === '/dashboard/resources' && tasks.publish) advanceOnboardingJourney(userId, 'manage')
    if (step === 'use' && hasUsedCurrentJourney) advanceOnboardingJourney(userId, 'useComplete')
  }, [hasUsedCurrentJourney, isDetail, isPublishing, navigate, pathname, step, tasks.publish, userId])

  useLayoutEffect(() => {
    if (!instruction) return
    setHasTarget(false)
    setDragPosition(null)
    let highlightedTarget: HTMLElement | null = null
    const findTarget = () => document.querySelector<HTMLElement>(`[data-onboarding-target="${instruction.target}"]`)
    const applyHighlight = (target: HTMLElement | null) => {
      if (highlightedTarget === target) return
      highlightedTarget?.classList.remove('relative', 'z-30', 'rounded-lg', 'ring-4', 'ring-primary/50', 'ring-offset-4')
      highlightedTarget = target
      target?.classList.add('relative', 'z-30', 'rounded-lg', 'ring-4', 'ring-primary/50', 'ring-offset-4')
      setHasTarget(Boolean(target))
      target?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
    }
    const update = () => {
      const target = findTarget()
      applyHighlight(target)
      setHasSearchResults(Boolean(document.querySelector('[data-onboarding-target="search-results"]')))
      const type = document.querySelector<HTMLElement>('[data-onboarding-resource-type]')?.dataset.onboardingResourceType
      setResourceType(type === 'SKILL' || type === 'AGENT' || type === 'TOOL' ? type : null)
      const panel = panelRef.current
      if (!target || !panel || window.innerWidth < 768) return setPosition(null)
      const rect = target.getBoundingClientRect(); const panelRect = panel.getBoundingClientRect(); const margin = 16
      const left = rect.right + panelRect.width + margin <= window.innerWidth ? rect.right + margin : Math.max(margin, rect.left - panelRect.width - margin)
      setPosition({ left, top: Math.min(Math.max(rect.top, margin), window.innerHeight - panelRect.height - margin) })
    }
    const frame = requestAnimationFrame(() => requestAnimationFrame(update))
    const observer = new MutationObserver(update)
    observer.observe(document.body, { childList: true, subtree: true })
    window.addEventListener('resize', update); window.addEventListener('scroll', update, true)
    return () => { cancelAnimationFrame(frame); observer.disconnect(); window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true); highlightedTarget?.classList.remove('relative', 'z-30', 'rounded-lg', 'ring-4', 'ring-primary/50', 'ring-offset-4') }
  }, [instruction])

  if (!step || !instruction) return null
  const guidance = actionGuidance(step, hasSearchResults)
  const usageGuidance = step === 'practice'
    ? resourceType === 'AGENT'
      ? '这是飞书 Agent：先在“使用说明”找到示例提问；点击“在飞书中使用”后，在打开的会话中把示例提问改成你的真实需求并发送。'
      : resourceType === 'TOOL'
        ? '这是工具：先确认需要准备的文件或参数；在线工具点击“立即使用”，下载型工具先下载并解压，再按使用说明打开或运行。'
        : '这是 Skill：先阅读示例任务；点击“复制安装”后，把命令粘贴到你使用的、支持 Skill 的 AI Agent 或终端中执行，再用自然语言提出实际任务。'
    : step === 'useComplete'
      ? resourceType === 'AGENT'
        ? '飞书会话已经打开：把刚才看到的示例提问改成你的真实需求，发送第一条消息。完成后回到本页点击“完成入门”。'
        : resourceType === 'TOOL'
          ? '工具入口已经打开：在线工具先按说明填写文件或参数；下载型工具解压后打开或运行。完成第一项操作后回到本页点击“完成入门”。'
          : '安装入口已经打开：把复制的安装命令粘贴到支持 Skill 的 AI Agent 或终端中执行，再用自然语言发起一次真实任务。完成后回到本页点击“完成入门”。'
      : null
  const needsUserAction = !hasTarget || (step === 'find' && (!tasks.discover || !hasSearchResults)) || (step === 'open' && !isDetail) || (step === 'use' && !hasUsedCurrentJourney) || (step === 'publishEntry' && !isPublishing)
  const next = () => {
    if (step === 'start') { advanceOnboardingJourney(userId, 'find'); navigate({ to: '/' }); return }
    if (step === 'find') { advanceOnboardingJourney(userId, 'open'); return }
    if (step === 'inspectHeader') { advanceOnboardingJourney(userId, 'inspectStatus'); return }
    if (step === 'inspectStatus') { advanceOnboardingJourney(userId, 'inspectTabs'); return }
    if (step === 'inspectTabs') { completeOnboardingTask(userId, 'detail'); advanceOnboardingJourney(userId, 'practice'); return }
    if (step === 'practice') { advanceOnboardingJourney(userId, 'use'); return }
    if (step === 'useComplete') { finishOnboardingJourney(userId); return }
    if (step === 'agents') { completeOnboardingTask(userId, 'agents'); advanceOnboardingJourney(userId, 'find'); navigate({ to: '/' }); return }
    if (step === 'tools') { completeOnboardingTask(userId, 'tools'); advanceOnboardingJourney(userId, 'find'); navigate({ to: '/' }); return }
    if (step === 'publishBasics') { advanceOnboardingJourney(userId, 'publishCategory'); return }
    if (step === 'publishCategory') { advanceOnboardingJourney(userId, 'publishDocumentation'); return }
    if (step === 'publishDocumentation') { advanceOnboardingJourney(userId, 'publishScope'); return }
    if (step === 'publishScope') { advanceOnboardingJourney(userId, 'publishSubmit'); return }
    if (step === 'publishSubmit') { finishOnboardingJourney(userId); return }
    if (step === 'manage') finishOnboardingJourney(userId)
  }
  const previous = () => {
    if (step === 'agents' || step === 'tools' || step === 'open' || step === 'inspectHeader') {
      advanceOnboardingJourney(userId, 'find')
      navigate({ to: '/' })
      return
    }
    const previousStep = journeySteps[Math.max(0, journeySteps.indexOf(step) - 1)]
    if (step === 'publishBasics') navigate({ to: '/dashboard/resources' })
    advanceOnboardingJourney(userId, previousStep)
  }
  const panelPosition = dragPosition ?? position
  const startDragging = (event: PointerEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).closest('button')) return
    const panel = panelRef.current
    if (!panel) return
    const rect = panel.getBoundingClientRect()
    dragRef.current = { pointerId: event.pointerId, offsetX: event.clientX - rect.left, offsetY: event.clientY - rect.top }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const drag = (event: PointerEvent<HTMLDivElement>) => {
    const dragging = dragRef.current
    const panel = panelRef.current
    if (!dragging || dragging.pointerId !== event.pointerId || !panel) return
    const rect = panel.getBoundingClientRect()
    const margin = 12
    setDragPosition({
      left: Math.min(Math.max(margin, event.clientX - dragging.offsetX), window.innerWidth - rect.width - margin),
      top: Math.min(Math.max(margin, event.clientY - dragging.offsetY), window.innerHeight - rect.height - margin),
    })
  }
  const stopDragging = (event: PointerEvent<HTMLDivElement>) => {
    if (dragRef.current?.pointerId !== event.pointerId) return
    dragRef.current = null
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
  return <aside ref={panelRef} role="region" aria-label="新手连续引导" className={panelPosition ? 'fixed z-[60] w-[min(24rem,calc(100vw-2rem))] rounded-xl border bg-white p-5 shadow-xl' : 'fixed bottom-5 right-5 z-[60] w-[min(24rem,calc(100vw-2rem))] rounded-xl border bg-white p-5 shadow-xl'} style={panelPosition ?? undefined}>
    <div className="flex cursor-grab touch-none gap-3 active:cursor-grabbing" onPointerDown={startDragging} onPointerMove={drag} onPointerUp={stopDragging} onPointerCancel={stopDragging}>
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">{journeySteps.indexOf(step) + 1}</span><div className="min-w-0 flex-1"><p className="text-xs font-semibold uppercase tracking-wide text-primary">开始使用 · {journeySteps.indexOf(step) + 1}/{journeySteps.length}</p><h2 className="mt-1 text-base font-semibold">{instruction.title}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{instruction.description}</p></div><button type="button" aria-label="暂停新手引导" onClick={() => pauseOnboardingJourney(userId)} className="cursor-pointer text-muted-foreground hover:text-foreground"><X className="h-4 w-4" /></button>
    </div>
    {guidance || usageGuidance ? <div className="mt-4 rounded-lg border border-primary/20 bg-primary/5 px-3 py-2.5 text-sm font-medium leading-6 text-foreground"><span className="mr-1.5 text-primary">现在要做：</span>{usageGuidance ?? guidance}</div> : null}
    <div className="mt-4 flex justify-between gap-3"><Button variant="outline" size="sm" disabled={journeySteps.indexOf(step) <= 0 || step === 'manage'} onClick={previous}><ChevronLeft className="mr-1 h-4 w-4" />上一步</Button><Button size="sm" disabled={needsUserAction} onClick={next}>{step === 'useComplete' || step === 'publishSubmit' || step === 'manage' ? <><CheckCircle2 className="mr-1 h-4 w-4" />完成入门</> : <>{instruction.action}<ChevronRight className="ml-1 h-4 w-4" /></>}</Button></div>
  </aside>
}
