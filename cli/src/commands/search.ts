import { JoyHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { AuthService } from '../services/auth-service'

export interface SearchCommandOptions {
  registry?: string
  token?: string
  limit?: number
  json?: boolean
}

export async function searchCommand(query: string, options: SearchCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  let token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const authService = new AuthService(configStore, credentialsStore)
  let reauthenticated = false
  if (!token) {
    const auth = await authService.ensure(registry)
    token = auth.token
    reauthenticated = true
  }

  let result
  try {
    result = await new JoyHubClient(registry, token).search(query ?? '', options.limit ?? 20)
  } catch (error) {
    if (
      reauthenticated ||
      !(error instanceof CliError) ||
      error.httpStatus !== 401
    ) {
      throw error
    }
    await credentialsStore.deleteToken(registry)
    const auth = await authService.ensure(registry)
    result = await new JoyHubClient(registry, auth.token).search(query ?? '', options.limit ?? 20)
  }
  if (options.json) {
    return JSON.stringify({ ok: true, items: result.items, total: result.total })
  }
  if (result.items.length === 0) return 'No skills found.'
  return result.items
    .map(item => `@${item.namespace}/${item.slug}  ${item.latestVersion ?? '-'}  ${item.summary ?? ''}`)
    .join('\n')
}
