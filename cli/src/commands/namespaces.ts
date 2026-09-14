import { JoyHubClient } from '../clients/skillhub-client'
import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'

export interface NamespacesCommandOptions {
  publishable?: boolean
  registry?: string
  token?: string
  json?: boolean
}

export async function namespacesCommand(options: NamespacesCommandOptions): Promise<string> {
  if (!options.publishable) {
    throw new CliError('the --publishable flag is required', EXIT.usage)
  }

  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  if (!token) {
    throw new CliError('authentication required to list publishable namespaces', EXIT.auth, {
      next: 'run `joyhub auth ensure`'
    })
  }

  const items = await new JoyHubClient(registry, token).publishTargets()
  if (options.json) {
    return JSON.stringify({ ok: true, items })
  }
  if (items.length === 0) {
    return 'No publishable namespaces.'
  }
  return items.map(item => `${item.slug}  ${item.displayName}  ${item.currentUserRole}`).join('\n')
}
