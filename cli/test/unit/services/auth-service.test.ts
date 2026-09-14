import { describe, expect, test } from 'bun:test'
import { AuthService } from '../../../src/services/auth-service'
import { ConfigStore } from '../../../src/stores/config-store'
import { CredentialsStore } from '../../../src/stores/credentials-store'
import { createTempHome } from '../../helpers/temp-env'

describe('AuthService', () => {
  test('clamps an invalid zero polling interval to one second without busy looping', async () => {
    const env = await createTempHome()
    const waits: number[] = []
    const originalFetch = globalThis.fetch
    globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input : input.url)
      if (url.pathname === '/api/v1/auth/device/code') {
        return Response.json({
          code: 0,
          data: {
            deviceCode: 'device',
            userCode: 'CODE',
            verificationUri: '/cli/auth',
            expiresIn: 10,
            interval: 0
          }
        })
      }
      if (url.pathname === '/api/v1/auth/device/token') {
        return Response.json({
          code: 0,
          data: { accessToken: 'jh_internal', tokenType: 'Bearer', error: null }
        })
      }
      if (url.pathname === '/api/cli/v1/auth/whoami') {
        expect(new Headers(init?.headers).get('authorization')).toBe('Bearer jh_internal')
        return Response.json({
          code: 0,
          data: { handle: 'tester', displayName: 'Tester' }
        })
      }
      return Response.json({ code: 404 }, { status: 404 })
    }) as typeof fetch

    try {
      const service = new AuthService(
        new ConfigStore(env.home),
        new CredentialsStore(env.home),
        {
          openBrowser: async () => false,
          sleep: async ms => {
            waits.push(ms)
          }
        }
      )
      const result = await service.ensure('https://joyhub.example.test')

      expect(result).toMatchObject({
        handle: 'tester',
        token: 'jh_internal',
        reauthenticated: true
      })
      expect(waits).toEqual([1000])
    } finally {
      globalThis.fetch = originalFetch
    }
  })
})
