import { redirect } from '@tanstack/react-router'

export type RouteLocationLike = {
  pathname: string
  searchStr?: string
  hash?: string
}

export function buildReturnTo(location: RouteLocationLike) {
  return `${location.pathname}${location.searchStr ?? ''}${location.hash ?? ''}`
}

export function createRequireAuth(getCurrentUser: () => Promise<unknown>) {
  return async function requireAuth({ location }: { location: RouteLocationLike }) {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({
        to: '/login',
        search: { returnTo: buildReturnTo(location) },
      })
    }
    return { user }
  }
}

/**
 * An OAuth callback can conservatively return to /login after the browser
 * session has been established. Send that browser to its intended in-app page
 * rather than making the employee press the login button a second time.
 */
export async function redirectAuthenticatedUser(
  getCurrentUser: () => Promise<unknown>,
  returnTo: string,
) {
  const user = await getCurrentUser()
  if (!user) {
    return
  }
  throw redirect({
    to: returnTo.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/',
  })
}
