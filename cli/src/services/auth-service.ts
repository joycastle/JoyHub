import { spawn } from 'node:child_process'
import { JoyHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

interface AuthServiceDependencies {
  openBrowser?: (url: string) => Promise<boolean>
  sleep?: (ms: number) => Promise<void>
  minimumPollIntervalMs?: number
}

interface AuthResult {
  handle: string
  token: string
}

export class AuthService {
  constructor(
    private readonly configStore: ConfigStore,
    private readonly credentialsStore: CredentialsStore,
    private readonly dependencies: AuthServiceDependencies = {}
  ) {}

  async login(registry: string, token?: string): Promise<{ handle: string }> {
    if (!token) {
      const result = await this.deviceLogin(registry)
      return { handle: result.handle }
    }
    const user = await new JoyHubClient(registry, token).whoami()
    await this.configStore.setRegistry(registry)
    await this.credentialsStore.setToken(registry, token)
    return { handle: user.handle }
  }

  async logout(registry: string): Promise<void> {
    await this.credentialsStore.deleteToken(registry)
  }

  async ensure(registry: string, token?: string): Promise<{
    handle: string
    token: string
    reauthenticated: boolean
  }> {
    if (token) {
      try {
        const user = await new JoyHubClient(registry, token).whoami()
        await this.configStore.setRegistry(registry)
        return { handle: user.handle, token, reauthenticated: false }
      } catch (error) {
        if (!(error instanceof CliError) || error.httpStatus !== 401) {
          throw error
        }
        await this.credentialsStore.deleteToken(registry)
      }
    }

    const user = await this.deviceLogin(registry)
    return { handle: user.handle, token: user.token, reauthenticated: true }
  }

  private async deviceLogin(registry: string): Promise<AuthResult> {
    const client = new JoyHubClient(registry)
    const device = await client.requestDeviceCode()
    const verificationUrl = buildVerificationUrl(registry, device.verificationUri, device.userCode)
    const browserOpened = await (this.dependencies.openBrowser ?? openBrowser)(verificationUrl)

    if (!browserOpened) {
      process.stderr.write(
        `Open this URL to authorize JoyHub:\n${verificationUrl}\nCode: ${device.userCode}\n`
      )
    } else {
      process.stderr.write(`Waiting for JoyHub authorization (code: ${device.userCode})...\n`)
    }

    const deadline = Date.now() + Math.max(0, device.expiresIn) * 1000
    const minimumPollIntervalMs = this.dependencies.minimumPollIntervalMs ?? 1000
    const serverIntervalMs = Number.isFinite(device.interval) ? device.interval * 1000 : 0
    const intervalMs = Math.max(minimumPollIntervalMs, serverIntervalMs)
    const wait = this.dependencies.sleep ?? sleep
    while (Date.now() < deadline) {
      await wait(Math.min(intervalMs, Math.max(0, deadline - Date.now())))
      if (Date.now() >= deadline) {
        break
      }
      const response = await client.pollDeviceToken(device.deviceCode)
      if (response.accessToken) {
        const user = await new JoyHubClient(registry, response.accessToken).whoami()
        await this.configStore.setRegistry(registry)
        await this.credentialsStore.setToken(registry, response.accessToken)
        return { handle: user.handle, token: response.accessToken }
      }
      if (response.error && response.error !== 'authorization_pending') {
        throw new CliError(`device authorization failed: ${response.error}`, EXIT.auth, {
          next: 'run `joyhub auth ensure` to retry'
        })
      }
    }

    throw new CliError('device authorization timed out', EXIT.auth, {
      next: 'run `joyhub auth ensure` to retry'
    })
  }
}

function buildVerificationUrl(registry: string, verificationUri: string, userCode: string): string {
  const url = new URL(verificationUri, `${registry}/`)
  if (!url.searchParams.has('user_code') && !url.searchParams.has('userCode')) {
    url.searchParams.set('user_code', userCode)
  }
  return url.toString()
}

async function openBrowser(url: string): Promise<boolean> {
  if (
    process.env.JOYHUB_NO_BROWSER === '1' ||
    process.env.CI === 'true' ||
    (process.platform !== 'win32' && process.platform !== 'darwin' && !process.env.DISPLAY && !process.env.WAYLAND_DISPLAY)
  ) {
    return Promise.resolve(false)
  }

  const command = process.platform === 'darwin'
    ? ['open', url]
    : process.platform === 'win32'
      ? ['cmd', '/c', 'start', '', url]
      : ['xdg-open', url]
  return new Promise(resolve => {
    let child
    try {
      child = spawn(command[0]!, command.slice(1), {
        detached: true,
        stdio: 'ignore'
      })
    } catch {
      resolve(false)
      return
    }
    child.once('error', () => resolve(false))
    child.once('spawn', () => {
      child.unref()
      resolve(true)
    })
  })
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}
