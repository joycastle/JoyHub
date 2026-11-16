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
 * session has been established. Return through a document navigation: the
 * target can be a server-owned OAuth endpoint, and its query must stay intact.
 */
export async function redirectAuthenticatedUser(
  getCurrentUser: () => Promise<unknown>,
  returnTo: string,
) {
  const user = await getCurrentUser()
  if (!user) {
    return
  }
  const safeTarget = returnTo.startsWith('/') && !returnTo.startsWith('//')
    && !returnTo.includes('\\') && ![...returnTo].some((character) => character.charCodeAt(0) <= 0x20) ? returnTo : '/'
  throw redirect({ href: safeTarget, reloadDocument: true })
}
