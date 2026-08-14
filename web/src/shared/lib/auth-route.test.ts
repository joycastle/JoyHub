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
        return isRedirect(error) && error.options.to === '/dashboard/resources'
      })
  })

  it('keeps the login route available when the browser has no session', async () => {
    await expect(redirectAuthenticatedUser(async () => null, '/dashboard/resources'))
      .resolves.toBeUndefined()
  })

  it('rejects unsafe authenticated return targets', async () => {
    await expect(redirectAuthenticatedUser(async () => ({ userId: 'user-1' }), 'https://evil.example'))
      .rejects.toSatisfy((error: unknown) => isRedirect(error) && error.options.to === '/')
  })
})
