import { open, readFile, writeFile, rename, rm } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { dirname } from 'node:path'
import { joinPath, userStateDir, ensureDir, applyCredentialPermissions, pathExists } from '../platform/paths'
import { OAuthCredentials, OAuthTokenError, refreshOAuthToken } from '../services/oauth-client'

interface CredentialsFile {
  tokens: Record<string, string>
  oauth?: Record<string, OAuthCredentials>
}

export class CredentialsStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'credentials.json')
  }

  async read(): Promise<CredentialsFile> {
    if (!(await pathExists(this.path))) return { tokens: {} }
    const parsed = JSON.parse(await readFile(this.path, 'utf-8')) as Partial<CredentialsFile>
    return {
      tokens: parsed.tokens ?? {},
      ...(parsed.oauth ? { oauth: parsed.oauth } : {})
    }
  }

  async getToken(registry: string): Promise<string | undefined> {
    return this.locked(() => this.getTokenUnlocked(registry))
  }

  private async getTokenUnlocked(registry: string): Promise<string | undefined> {
    const current = await this.read()
    const token = current.tokens[registry]
    const oauth = current.oauth?.[registry]
    if (!token || !oauth || oauth.expiresAt > Date.now() + 30_000) {
      return token
    }

    try {
      const refreshed = await refreshOAuthToken(registry, oauth)
      await this.setOAuthSessionUnlocked(registry, refreshed.accessToken, {
        ...oauth,
        refreshToken: refreshed.refreshToken,
        expiresAt: Date.now() + refreshed.expiresIn * 1000,
        scope: refreshed.scope,
        tokenType: refreshed.tokenType
      })
      return refreshed.accessToken
    } catch (error) {
      if (error instanceof OAuthTokenError && error.errorCode === 'invalid_grant') {
        await this.deleteTokenUnlocked(registry)
        return undefined
      }
      throw error
    }
  }

  async setToken(registry: string, token: string): Promise<void> {
    return this.locked(() => this.setTokenUnlocked(registry, token))
  }

  private async setTokenUnlocked(registry: string, token: string): Promise<void> {
    const current = await this.read()
    const oauth = { ...(current.oauth ?? {}) }
    delete oauth[registry]
    await ensureDir(dirname(this.path))
    await this.writeCredentials({
      tokens: { ...current.tokens, [registry]: token },
      ...(Object.keys(oauth).length > 0 ? { oauth } : {})
    })
  }

  async setOAuthSession(registry: string, token: string, credentials: OAuthCredentials): Promise<void> {
    return this.locked(() => this.setOAuthSessionUnlocked(registry, token, credentials))
  }

  private async setOAuthSessionUnlocked(registry: string, token: string, credentials: OAuthCredentials): Promise<void> {
    const current = await this.read()
    await ensureDir(dirname(this.path))
    await this.writeCredentials({
      tokens: { ...current.tokens, [registry]: token },
      oauth: { ...(current.oauth ?? {}), [registry]: credentials }
    })
  }

  async getOAuthCredentials(registry: string): Promise<OAuthCredentials | undefined> {
    return (await this.read()).oauth?.[registry]
  }

  async deleteToken(registry: string): Promise<void> {
    return this.locked(() => this.deleteTokenUnlocked(registry))
  }

  private async deleteTokenUnlocked(registry: string): Promise<void> {
    const current = await this.read()
    const tokens = { ...current.tokens }
    const oauth = { ...(current.oauth ?? {}) }
    delete tokens[registry]
    delete oauth[registry]
    await ensureDir(dirname(this.path))
    await this.writeCredentials({
      tokens,
      ...(Object.keys(oauth).length > 0 ? { oauth } : {})
    })
  }

  private async writeCredentials(value: CredentialsFile): Promise<void> {
    const temporary = `${this.path}.${randomUUID()}.tmp`
    try {
      await writeFile(temporary, JSON.stringify(value, null, 2), { mode: 0o600 })
      await applyCredentialPermissions(temporary)
      await rename(temporary, this.path)
    } finally {
      await rm(temporary, { force: true })
    }
  }

  private async locked<T>(operation: () => Promise<T>): Promise<T> {
    await ensureDir(dirname(this.path))
    const lockPath = `${this.path}.lock`
    let handle: Awaited<ReturnType<typeof open>> | undefined
    for (let attempt = 0; attempt < 400; attempt++) {
      try {
        handle = await open(lockPath, 'wx', 0o600)
        await handle.writeFile(String(process.pid))
        break
      } catch (error) {
        if (!(error instanceof Error) || !('code' in error) || error.code !== 'EEXIST') throw error
        const pid = Number(await readFile(lockPath, 'utf8').catch(() => ''))
        if (Number.isSafeInteger(pid) && pid > 0) {
          try { process.kill(pid, 0) } catch (probeError) {
            if (probeError instanceof Error && 'code' in probeError && probeError.code === 'ESRCH') {
              await rm(lockPath, { force: true })
              continue
            }
          }
        }
        await new Promise(resolve => setTimeout(resolve, 100))
      }
    }
    if (!handle) throw new Error('JoyHub credentials are busy; retry the command')
    try { return await operation() } finally {
      await handle.close()
      await rm(lockPath, { force: true })
    }
  }
}
