import { printResult } from '../shared/output'

export const commands = {
  help: {
    summary: 'Show available commands',
    usage: 'joyhub help [command] [--json]',
    examples: ['joyhub help', 'joyhub help install', 'joyhub help --json']
  },
  version: {
    summary: 'Show installed CLI version',
    usage: 'joyhub version [--json]',
    examples: ['joyhub version', 'joyhub version --json']
  },
  auth: {
    summary: 'Ensure a valid JoyHub login',
    usage: 'joyhub auth ensure [--registry <url>] [--json]',
    examples: ['joyhub auth ensure', 'joyhub auth ensure --json']
  },
  login: {
    summary: 'Save registry and token',
    usage: 'joyhub login [--token <token>] [--registry <url>] [--json]',
    examples: ['joyhub login --token jh_xxx', 'joyhub login --registry https://joyhub.example.com']
  },
  logout: {
    summary: 'Remove local token',
    usage: 'joyhub logout [--registry <url>] [--json]',
    examples: ['joyhub logout']
  },
  whoami: {
    summary: 'Verify current token',
    usage: 'joyhub whoami [--token <token>] [--registry <url>] [--json]',
    examples: ['joyhub whoami', 'joyhub whoami --json']
  },
  search: {
    summary: 'Search published skills',
    usage: 'joyhub search [query] [--query <query>] [--limit <n>] [--registry <url>] [--token <token>] [--json]',
    examples: ['joyhub search --query pdf', 'joyhub search pdf --token jh_xxx']
  },
  namespaces: {
    summary: 'List namespaces',
    usage: 'joyhub namespaces --publishable [--registry <url>] [--token <token>] [--json]',
    examples: ['joyhub namespaces --publishable --json']
  },
  install: {
    summary: 'Install a skill locally',
    usage: 'joyhub install <coordinate> [--scope <user|project>] [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--json]',
    examples: [
      'joyhub install pdf-parser',
      'joyhub install team/my-skill',
      'joyhub install @team/my-skill',
      'joyhub install team--my-skill',
      'joyhub install pdf-parser --scope user',
      'joyhub install pdf-parser --scope project --agent codex'
    ]
  },
  list: {
    summary: 'List local installs',
    usage: 'joyhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]',
    examples: ['joyhub list', 'joyhub list --agent codex']
  },
  remove: {
    summary: 'Remove local or remote skill',
    usage: 'joyhub remove <coordinate> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--json]',
    examples: [
      'joyhub remove pdf-parser',
      'joyhub remove team/my-skill',
      'joyhub remove my-skill --namespace team',
      'joyhub remove pdf-parser --remote --hard'
    ]
  },
  doctor: {
    summary: 'Scan project and merge into local inventory (preserves entries outside scan scope)',
    usage: 'joyhub doctor [--json]',
    examples: ['joyhub doctor', 'joyhub doctor --json']
  },
  publish: {
    summary: 'Publish a local skill package',
    usage: 'joyhub publish <path> [--namespace <slug>] [--visibility <public|namespace-only|private>] [--registry <url>] [--json]',
    examples: ['joyhub publish ./my-skill', 'joyhub publish ./my-skill --namespace myspace']
  },
  update: {
    summary: 'Check or update CLI itself',
    usage: 'joyhub update [--check] [--json]',
    examples: ['joyhub update --check', 'joyhub update']
  }
} as const

export function formatCommandList(): string {
  return Object.entries(commands).map(([name, detail]) => `${name.padEnd(10)} ${detail.summary}`).join('\n')
}

export async function helpCommand(args: string[]): Promise<string> {
  const json = args.includes('--json')
  const topic = args.find(arg => !arg.startsWith('--'))
  if (json) {
    if (topic) {
      // TODO: unknown topic returns undefined and crashes on detail.usage; see help-command.test.ts
      const detail = commands[topic as keyof typeof commands]
      return printResult({ ok: true, command: topic, ...detail }, true)
    }
    return printResult({
      ok: true,
      commands: Object.entries(commands).map(([name, detail]) => ({ name, description: detail.summary }))
    }, true)
  }
  if (topic) {
    const detail = commands[topic as keyof typeof commands]
    return [
      `${topic} - ${detail.summary}`,
      `Usage: ${detail.usage}`,
      'Examples:',
      ...detail.examples.map(example => `  ${example}`)
    ].join('\n')
  }
  return formatCommandList()
}
