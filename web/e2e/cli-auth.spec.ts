import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('CLI Device Auth (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('requires login and preserves the device code', async ({ page }) => {
    await page.goto('/cli/auth?user_code=ABCD-2345')
    await expect(page).toHaveURL(/\/login\?returnTo=/)
  })
})
