import { createServer } from 'node:http'
import { discoverOAuthServer, registerOAuthClient, exchangeOAuthCode, revokeOAuthToken, createPkcePair, resourceForRegistry, OAUTH_SCOPE, type OAuthServerMetadata } from './oauth-client'
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
      const result = await this.interactiveLogin(registry)
      return { handle: result.handle }
    }
    const user = await new JoyHubClient(registry, token).whoami()
    await this.configStore.setRegistry(registry)
    await this.credentialsStore.setToken(registry, token)
    return { handle: user.handle }
  }

  async logout(registry: string): Promise<void> {
    const oauth = await this.credentialsStore.getOAuthCredentials(registry)
    try {
      if (oauth) {
        const metadata = await discoverOAuthServer(registry)
        if (metadata) await revokeOAuthToken(metadata, oauth, resourceForRegistry(registry))
      }
    } finally {
      await this.credentialsStore.deleteToken(registry)
    }
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

    const user = await this.interactiveLogin(registry)
    return { handle: user.handle, token: user.token, reauthenticated: true }
  }

  private async interactiveLogin(registry: string): Promise<AuthResult> {
    const metadata = await discoverOAuthServer(registry)
    return metadata ? this.oauthLogin(registry, metadata) : this.deviceLogin(registry)
  }

  private async oauthLogin(registry: string, metadata: OAuthServerMetadata): Promise<AuthResult> {
    const pkce = createPkcePair()
    let complete: (code: string) => void = () => {}
    let fail: (error: Error) => void = () => {}
    const codePromise = new Promise<string>((resolve, reject) => { complete = resolve; fail = reject })
    const server = createServer((request, response) => {
      const address = server.address()
      if (!address || typeof address === 'string') { response.writeHead(400).end(); return }
      const callbackOrigin = `http://127.0.0.1:${address.port}`
      const url = new URL(request.url ?? '/', callbackOrigin)
      if (request.method !== 'GET' || url.pathname !== '/callback' || request.headers.host !== `127.0.0.1:${address.port}`) {
        response.writeHead(404).end(); return
      }
      if (url.searchParams.getAll('state').length !== 1 || url.searchParams.get('state') !== pkce.state) {
        response.writeHead(400).end('Invalid authorization state'); return
      }
      const issuer = url.searchParams.get('iss')
      if (issuer && issuer !== metadata.issuer) { response.writeHead(400).end('Invalid issuer'); return }
      if (url.searchParams.has('error')) {
        response.writeHead(400).end('Authorization was not granted')
        fail(new CliError('JoyHub authorization was not granted', EXIT.auth)); return
      }
      const code = url.searchParams.get('code')
      if (!code || url.searchParams.getAll('code').length !== 1) { response.writeHead(400).end('Missing authorization code'); return }
      response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' })
        .end('JoyHub authorization received. You can close this window and return to the terminal.')
      complete(code)
    })
    // Attach rejection handling before starting external requests or opening the browser.
    void codePromise.catch(() => {})
    let timer: ReturnType<typeof setTimeout> | undefined
    try {
      await new Promise<void>((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve) })
      const address = server.address()
      if (!address || typeof address === 'string') throw new CliError('Could not start OAuth callback listener', EXIT.auth)
      const redirectUri = `http://127.0.0.1:${address.port}/callback`
      const registration = await registerOAuthClient(metadata.registrationEndpoint, redirectUri)
      const url = new URL(metadata.authorizationEndpoint)
      url.search = new URLSearchParams({ response_type: 'code', client_id: registration.clientId,
        redirect_uri: redirectUri, scope: OAUTH_SCOPE, state: pkce.state,
        code_challenge_method: 'S256', code_challenge: pkce.codeChallenge, resource: resourceForRegistry(registry) }).toString()
      timer = setTimeout(() => fail(new CliError('JoyHub authorization timed out', EXIT.auth)), 300_000)
      const opened = await (this.dependencies.openBrowser ?? openBrowser)(url.toString())
      process.stderr.write(opened ? 'Waiting for JoyHub browser authorization...\n' : `Open this URL to authorize JoyHub:\n${url}\n`)
      const code = await codePromise
      const tokens = await exchangeOAuthCode(metadata, registration, redirectUri, code, pkce.codeVerifier, resourceForRegistry(registry))
      const user = await new JoyHubClient(registry, tokens.accessToken).whoami()
      await this.configStore.setRegistry(registry)
      await this.credentialsStore.setOAuthSession(registry, tokens.accessToken, {
        clientId: registration.clientId, clientSecret: registration.clientSecret, refreshToken: tokens.refreshToken,
        expiresAt: Date.now() + tokens.expiresIn * 1000, scope: tokens.scope, tokenType: tokens.tokenType
      })
      return { handle: user.handle, token: tokens.accessToken }
    } finally {
      if (timer) clearTimeout(timer)
      server.closeAllConnections()
      server.close()
    }
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
