import { describe, expect, it, vi } from 'vitest'
import { isRedirect } from '@tanstack/react-router'
import { buildReturnTo, createRequireAuth, redirectAuthenticatedUser } from './auth-route'

describe('auth-route', () => {
  it('buildReturnTo preserves pathname search and hash', () => {
    expect(buildReturnTo({
      pathname: '/space/global/caldav-calendar',
      searchStr: '?tab=files',
      hash: '#readme',
    })).toBe('/space/global/caldav-calendar?tab=files#readme')
  })

  it('createRequireAuth redirects unauthenticated users to login with returnTo', async () => {
    const requireAuth = createRequireAuth(async () => null)

    await expect(requireAuth({
      location: {
        pathname: '/space/global/caldav-calendar',
        searchStr: '?tab=files',
        hash: '#readme',
      },
    })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) {
        return false
      }
      expect(error.options.to).toBe('/login')
      expect(error.options.search).toEqual({
        returnTo: '/space/global/caldav-calendar?tab=files#readme',
      })
      return true
    })
  })

  it('createRequireAuth returns the current user when authenticated', async () => {
    const user = { userId: 'user-1' }
    const getCurrentUser = vi.fn(async () => user)
    const requireAuth = createRequireAuth(getCurrentUser)

    await expect(requireAuth({
      location: { pathname: '/dashboard' },
    })).resolves.toEqual({ user })
    expect(getCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('redirects an authenticated OAuth callback away from the login route', async () => {
    await expect(redirectAuthenticatedUser(async () => ({ userId: 'user-1' }), '/dashboard/resources'))
      .rejects.toSatisfy((error: unknown) => {
        expect(isRedirect(error)).toBe(true)
        return isRedirect(error) && error.options.href === '/dashboard/resources'
          && error.options.reloadDocument === true
      })
  })

  it('returns server-owned authorization URLs to the server with their original query', async () => {
    const target = '/oauth2/authorize?client_id=client&redirect_uri=http%3A%2F%2F127.0.0.1%3A12345%2Fcallback&state=a%2Bb%3D&code_challenge=challenge&scope=resource%3Aread+offline_access'
    await expect(redirectAuthenticatedUser(async () => ({ userId: 'user-1' }), target))
      .rejects.toSatisfy((error: unknown) => isRedirect(error)
        && error.options.href === target && error.options.reloadDocument === true)
  })

  it('keeps the login route available when the browser has no session', async () => {
    await expect(redirectAuthenticatedUser(async () => null, '/dashboard/resources'))
      .resolves.toBeUndefined()
  })

  it.each(['https://evil.example', '//evil.example', '/\\evil.example', '/\tevil.example', ''])('rejects unsafe authenticated return target %j', async (target) => {
    await expect(redirectAuthenticatedUser(async () => ({ userId: 'user-1' }), target))
      .rejects.toSatisfy((error: unknown) => isRedirect(error) && error.options.href === '/')
  })
})
