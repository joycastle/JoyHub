import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('namespaces command', () => {
  test('lists publishable namespaces as JSON', async () => {
    registry = await startFakeRegistry({
      token: 'jh_ok',
      publishTargets: [{
        id: 7,
        slug: 'data-team',
        displayName: 'Data Team',
        currentUserRole: 'MEMBER',
        supportedResourceTypes: ['SKILL']
      }]
    })

    const result = await runCli([
      'namespaces',
      '--publishable',
      '--registry',
      registry.url,
      '--token',
      'jh_ok',
      '--json'
    ])

    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({
      ok: true,
      items: [{ slug: 'data-team', currentUserRole: 'MEMBER' }]
    })
  })

  test('requires a token', async () => {
    registry = await startFakeRegistry({})
    const result = await runCli(['namespaces', '--publishable', '--registry', registry.url])
    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('authentication required')
  })
})
