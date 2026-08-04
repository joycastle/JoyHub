import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { loginWithCredentials, registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

function getOptionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function adminCredentials() {
  return {
    username: getOptionalEnv('E2E_ADMIN_USERNAME') ?? getOptionalEnv('BOOTSTRAP_ADMIN_USERNAME') ?? 'admin',
    password: getOptionalEnv('E2E_ADMIN_PASSWORD') ?? getOptionalEnv('BOOTSTRAP_ADMIN_PASSWORD') ?? 'ChangeMe!2026',
  }
}

test.describe('Rejected version replacement (Real API)', () => {
  test.describe.configure({ timeout: 150_000 })

  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('re-publishes the same version after rejection', async ({ page, browser }, testInfo) => {
    const publisherBuilder = new E2eTestDataBuilder(page, testInfo)
    await publisherBuilder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    try {
      const namespace = await publisherBuilder.ensureWritableNamespace()
      const skillName = `replace-rejected-${Date.now().toString(36)}`
      const firstPublish = await publisherBuilder.publishSkill(namespace.slug, {
        name: skillName,
        version: '1.0.0',
      })
      const rejectedReviewId = await adminBuilder.waitForPendingReview(
        namespace.slug,
        firstPublish.slug,
        firstPublish.version,
      )
      await publisherBuilder.waitForVersionStatus(
        namespace.slug,
        firstPublish.slug,
        firstPublish.version,
        'PENDING_REVIEW',
      )
      await adminBuilder.rejectReview(rejectedReviewId)

      const replacement = await publisherBuilder.publishSkill(namespace.slug, {
        name: skillName,
        description: 'Replacement after review rejection',
        version: '1.0.0',
      })
      const replacementReviewId = await adminBuilder.waitForPendingReview(
        namespace.slug,
        replacement.slug,
        replacement.version,
      )
      await publisherBuilder.waitForVersionStatus(
        namespace.slug,
        replacement.slug,
        replacement.version,
        'PENDING_REVIEW',
      )

      expect(replacement.skillId).toBe(firstPublish.skillId)
      expect(replacement.version).toBe(firstPublish.version)
      expect(replacementReviewId).not.toBe(rejectedReviewId)

      const replacedReviewResponse = await adminPage.request.get(`/api/web/reviews/${rejectedReviewId}`)
      expect(replacedReviewResponse.status()).toBe(404)
    } finally {
      await adminBuilder.cleanup()
      await adminContext.close()
      await publisherBuilder.cleanup()
    }
  })
})
