import { expect, test } from '@playwright/test'

test('an existing browser session resumes OAuth through a document request with its full query', async ({ page }) => {
  await page.route('**/api/v1/auth/me', async (route) => route.fulfill({
    json: { code: 0, data: { userId: 'oauth-return-user', displayName: 'OAuth Return User', platformRoles: ['USER'] } },
  }))
  const target = '/oauth2/authorize?client_id=client&redirect_uri=http%3A%2F%2F127.0.0.1%3A12345%2Fcallback&state=a%2Bb%3D&code_challenge=challenge&scope=resource%3Aread+offline_access'
  let resumed = false
  await page.route('**/oauth2/authorize?**', async (route) => {
    expect(route.request().isNavigationRequest()).toBe(true)
    const url = new URL(route.request().url())
    expect(url.pathname + url.search).toBe(target)
    resumed = true
    await route.fulfill({ contentType: 'text/html', body: '<h1>Authorization endpoint reached</h1>' })
  })
  await page.goto(`/login?returnTo=${encodeURIComponent(target)}`)
  await expect(page.getByRole('heading', { name: 'Authorization endpoint reached' })).toBeVisible()
  expect(resumed).toBe(true)
})
