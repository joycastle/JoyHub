import { afterEach, describe, expect, mock, test } from 'bun:test'
import type { AgentCandidate } from '../../../src/agents/types'

interface PromptChoice {
  value: AgentCandidate
}

interface PromptOptions {
  choices?: PromptChoice[]
  onRender?: (this: { cursor?: number }) => void
  format?: (selectedTargets: AgentCandidate[]) => AgentCandidate[]
}

const defaultSelectedTargets = (options: PromptOptions): AgentCandidate[] => options.format?.([]) ?? []
let selectPromptTargets = defaultSelectedTargets

mock.module('prompts', () => ({
  default: (options: PromptOptions) => {
    options.onRender?.call({ cursor: 1 })
    return { selected: selectPromptTargets(options) }
  }
}))

afterEach(() => {
  selectPromptTargets = defaultSelectedTargets
})

const { resolveInstallTargets } = await import('../../../src/agents/resolver')

describe('resolveInstallTargets interactive prompt', () => {
  test('uses the highlighted target when Enter submits an empty multiselect', async () => {
    const detected: AgentCandidate[] = [
      { agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' },
      { agent: 'claude-code', rootDir: '/repo/.claude/skills', scope: 'project', source: 'detected' }
    ]
    const highlighted = detected[1]!

    const targets = await resolveInstallTargets({
      cwd: '/repo',
      agents: [],
      json: false,
      interactive: true,
      detected
    })

    expect(targets).toEqual([highlighted])
  })

  test('allows selecting generic alongside detected user targets', async () => {
    selectPromptTargets = options => options.choices?.map(choice => choice.value) ?? []
    const codex: AgentCandidate = {
      agent: 'codex',
      rootDir: '/home/u/.codex/skills',
      scope: 'user',
      source: 'detected'
    }
    const generic: AgentCandidate = {
      agent: 'generic',
      rootDir: '/home/u/.agents/skills',
      scope: 'user',
      source: 'fallback'
    }

    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: [],
      scope: 'user',
      json: false,
      interactive: true,
      detected: [codex]
    })

    expect(targets).toEqual([codex, generic])
  })
})
