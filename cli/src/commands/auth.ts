import { AuthService } from '../services/auth-service'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'

export interface AuthEnsureCommandOptions {
  registry?: string
  token?: string
  json?: boolean
}

export async function authEnsureCommand(options: AuthEnsureCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const result = await new AuthService(configStore, credentialsStore).ensure(registry, token)

  return options.json
    ? JSON.stringify({
        ok: true,
        authenticated: true,
        registry,
        handle: result.handle,
        reauthenticated: result.reauthenticated
      })
    : `Authenticated with ${registry} as ${result.handle}`
}
