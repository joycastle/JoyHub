import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'

describe('landing quick start locales', () => {
  it('uses localized agent setup prompts for chinese and english', () => {
    expect(zh.landing.quickStart.agent.command).toBe('阅读 https://www.example.com/registry/skill.md，并按照说明完成 SkillHub Skills Registry 的配置')
    expect(en.landing.quickStart.agent.command).toBe('Read https://www.example.com/registry/skill.md and follow the instructions to setup SkillHub Skills Registry')
  })

  it('provides command templates with url placeholder for dynamic rendering', () => {
    expect(zh.landing.quickStart.agent.commandTemplate).toBe('阅读 {{url}}，并按照说明完成 SkillHub Skills Registry 的配置')
    expect(en.landing.quickStart.agent.commandTemplate).toBe('Read {{url}} and follow the instructions to setup SkillHub Skills Registry')
  })

  it('exposes the on-demand JoyHub CLI command in both locales', () => {
    expect(zh.landing.quickStart.tabs.cli).toBe('CLI')
    expect(zh.landing.quickStart.cli.command).toBe('npx --yes --package=@toolnets/joyhub-cli@0.2.0 joyhub auth ensure')
    expect(zh.landing.quickStart.cli.description).toBe('按需运行 JoyHub CLI，无需全局安装')
    expect(en.landing.quickStart.tabs.cli).toBe('CLI')
    expect(en.landing.quickStart.cli.command).toBe('npx --yes --package=@toolnets/joyhub-cli@0.2.0 joyhub auth ensure')
    expect(en.landing.quickStart.cli.description).toBe('Run JoyHub CLI on demand without a global install.')
  })
})
