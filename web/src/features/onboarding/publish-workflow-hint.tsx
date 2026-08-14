import { useState } from 'react'
import { ChevronDown, ChevronUp, ListChecks } from 'lucide-react'

const CONTENT = {
  skill: [
    ['选择空间', '先选择维护这个 Skill 的团队或公共库；团队成员和可见范围会决定谁能搜索、安装它。'],
    ['准备 Skill 包', '上传 ZIP。压缩包根目录必须包含 SKILL.md；其中的名称、简介和使用步骤是同事安装后最先看到的内容。'],
    ['检查元数据和安全提示', '确认适用场景、版本与可见范围；如出现扫描或格式提示，先修正再提交，不要跳过不理解的警告。'],
    ['发布后验证', '发布后到“我的内容”确认状态。打开详情页检查安装命令和文档；后续更新请发布新版本，而不是只改口头说明。'],
  ],
  resource: [
    ['说明它能解决什么', '填写名称、简介和适用场景。它们决定同事能否搜到这项 Agent 或工具。'],
    ['配置真实使用入口', '飞书 Agent 填 App ID；在线工具填公开访问地址；需要下载的工具上传 ZIP。不要填写测试或个人地址。'],
    ['让同事能独立使用', '在“使用说明”写清准备什么输入、怎么开始、能得到什么结果和限制。可用 AI 草稿，但必须人工核对。'],
    ['指定所属部门并发布', '选择所属部门；部门库内容对部门内全体人员可见。发布后去“我的内容”检查状态、编辑入口、下架或归档。'],
  ],
} as const

/** A detailed, persistent publish reference that lets new users fill the real form without a blocking overlay. */
export function PublishWorkflowHint({ kind }: { kind: 'skill' | 'resource' }) {
  const [expanded, setExpanded] = useState(true)
  return (
    <section className="rounded-lg border bg-[#f8fafc] px-5 py-4">
      <button type="button" className="flex w-full items-center justify-between gap-4 text-left" onClick={() => setExpanded((value) => !value)}>
        <span className="flex items-center gap-2 font-semibold"><ListChecks className="h-4 w-4 text-primary" />手把手发布（共 4 步）</span>
        {expanded ? <ChevronUp className="h-4 w-4 text-muted-foreground" /> : <ChevronDown className="h-4 w-4 text-muted-foreground" />}
      </button>
      {expanded ? <ol className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{CONTENT[kind].map(([title, description], index) => <li key={title} className="flex gap-2 text-sm"><span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">{index + 1}</span><span><strong className="block text-foreground">{title}</strong><span className="mt-0.5 block leading-5 text-muted-foreground">{description}</span></span></li>)}</ol> : null}
    </section>
  )
}
