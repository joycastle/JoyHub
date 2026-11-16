import { describe, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { stat } from 'node:fs/promises'
import { AuthService } from '../../../src/services/auth-service'
import { ConfigStore } from '../../../src/stores/config-store'
import { CredentialsStore } from '../../../src/stores/credentials-store'
import { discoverOAuthServer } from '../../../src/services/oauth-client'
import { createTempHome } from '../../helpers/temp-env'

describe('shared OAuth login', () => {
  test('completes PKCE callback, refreshes once for concurrent reads, and revokes on logout', async () => {
    const env = await createTempHome()
    const registry = 'https://joyhub.example.test'
    const store = new CredentialsStore(env.home)
    const originalFetch = globalThis.fetch
    let challenge = ''
    let redirect = ''
    let refreshes = 0
    let revoked = false
    globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input : input.url)
      const form = new URLSearchParams(typeof init?.body === 'string' ? init.body : '')
      if (url.host.startsWith('127.0.0.1:')) return originalFetch(input, init)
      if (url.pathname === '/.well-known/oauth-authorization-server') return Response.json({
        issuer: registry, authorization_endpoint: `${registry}/oauth2/authorize`, token_endpoint: `${registry}/oauth2/token`,
        registration_endpoint: `${registry}/oauth2/register`, revocation_endpoint: `${registry}/oauth2/revoke`, code_challenge_methods_supported: ['S256']
      })
      if (url.pathname === '/oauth2/register') {
        const request = JSON.parse(String(init?.body)) as { redirect_uris: string[] }
        redirect = request.redirect_uris[0]!
        expect(new URL(redirect).hostname).toBe('127.0.0.1')
        return Response.json({ client_id: 'client', client_secret: 'local-secret', token_endpoint_auth_method: 'client_secret_post',
          scope: 'skill:publish resource:read skill:read offline_access' }, { status: 201 })
      }
      if (url.pathname === '/oauth2/token') {
        expect(form.get('resource')).toBe(`${registry}/mcp`)
        expect(form.get('client_secret')).toBe('local-secret')
        if (form.get('grant_type') === 'authorization_code') {
          expect(form.get('code')).toBe('one-time-code')
          expect(createHash('sha256').update(form.get('code_verifier')!).digest('base64url')).toBe(challenge)
          expect(form.get('redirect_uri')).toBe(redirect)
        } else {
          refreshes++
          expect(form.get('refresh_token')).toBe('refresh-one')
        }
        return Response.json({ access_token: refreshes ? 'access-two' : 'access-one', refresh_token: refreshes ? 'refresh-two' : 'refresh-one',
          token_type: 'Bearer', expires_in: 1800, scope: 'resource:read skill:read skill:publish' })
      }
      if (url.pathname === '/api/cli/v1/auth/whoami') return Response.json({ code: 0, data: { handle: 'user' } })
      if (url.pathname === '/oauth2/revoke') {
        expect(form.get('token')).toBe('refresh-two'); revoked = true
        return new Response(null, { status: 200 })
      }
      return Response.json({}, { status: 404 })
    }) as typeof fetch
    try {
      const auth = new AuthService(new ConfigStore(env.home), store, { openBrowser: async authorization => {
        const url = new URL(authorization)
        expect(url.searchParams.get('code_challenge_method')).toBe('S256')
        challenge = url.searchParams.get('code_challenge')!
        const callback = new URL(url.searchParams.get('redirect_uri')!)
        callback.search = new URLSearchParams({ state: 'wrong', code: 'ignored' }).toString()
        expect((await originalFetch(callback)).status).toBe(400)
        callback.search = new URLSearchParams({ state: url.searchParams.get('state')!, code: 'one-time-code' }).toString()
        expect((await originalFetch(callback)).status).toBe(200)
        return true
      } })
      expect(await auth.login(registry)).toEqual({ handle: 'user' })
      const oauth = (await store.getOAuthCredentials(registry))!
      await store.setOAuthSession(registry, 'access-one', { ...oauth, expiresAt: Date.now() - 1 })
      expect(await Promise.all([store.getToken(registry), store.getToken(registry)])).toEqual(['access-two', 'access-two'])
      expect(refreshes).toBe(1)
      if (process.platform !== 'win32') expect((await stat(store.path)).mode & 0o777).toBe(0o600)
      await auth.logout(registry)
      expect(revoked).toBe(true)
      expect(await store.getToken(registry)).toBeUndefined()
    } finally { globalThis.fetch = originalFetch }
  })

  test('rejects a discovery document that sends credentials to a different origin', async () => {
    await expect(discoverOAuthServer('https://joyhub.example.test', { fetchImpl: (async () => Response.json({
      issuer: 'https://joyhub.example.test', authorization_endpoint: 'https://elsewhere.example/authorize',
      token_endpoint: 'https://elsewhere.example/token', registration_endpoint: 'https://elsewhere.example/register',
      revocation_endpoint: 'https://elsewhere.example/revoke', code_challenge_methods_supported: ['S256']
    })) as unknown as typeof fetch })).rejects.toThrow('missing required PKCE endpoints')
  })
})
