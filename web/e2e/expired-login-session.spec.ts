import { expect, test } from '@playwright/test'

test('a login entry 401 stays on the login page and preserves the pending client return', async ({ page }) => {
  const pendingReturn = '/oauth2/authorize?client_id=test&state=original-client-state'
  const login = `/login?returnTo=${encodeURIComponent(pendingReturn)}`
  let documentLoads = 0
  let methodRequests = 0
  page.on('request', (request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame()) documentLoads++
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/methods') methodRequests++
    await route.fulfill({ status: 401, json: { code: 401, msg: 'Session expired' } })
  })
  await page.goto(login)
  await expect.poll(() => methodRequests).toBeGreaterThan(0)
  // The query may retry once; wait beyond that interval to detect full-document reload loops.
  await expect(page.getByRole('button', { name: /加载中|Loading/i })).toHaveCount(0, { timeout: 10_000 })
  await expect(page).toHaveURL(new RegExp('returnTo='))
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe(pendingReturn)
  expect(documentLoads).toBe(1)
})
