import { chmod, mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { createTempHome } from '../helpers/temp-env'
import { runCli } from '../helpers/run-cli'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('auth ensure', () => {
  test('completes device flow, saves mode 0600, and never emits the token', async () => {
    const env = await createTempHome()
    const secret = 'jh_secret_device_token'
    registry = await startFakeRegistry({
      token: secret,
      deviceFlow: {
        accessToken: secret,
        userCode: 'ABCD-1234',
        verificationUri: '/cli/auth',
        pendingPolls: 1
      }
    })

    const result = await runCli(['auth', 'ensure', '--registry', registry.url, '--json'], {
      HOME: env.home,
      USERPROFILE: env.home,
      JOYHUB_NO_BROWSER: '1'
    })

    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({
      ok: true,
      authenticated: true,
      reauthenticated: true
    })
    expect(result.stderr).toContain(`${registry.url}/cli/auth?user_code=ABCD-1234`)
    expect(result.stderr).toContain('Code: ABCD-1234')
    expect(result.stdout + result.stderr).not.toContain(secret)
    expect(await Bun.file(join(env.home, '.joyhub', 'credentials.json')).json())
      .toMatchObject({ tokens: { [registry.url]: secret } })
    if (process.platform !== 'win32') {
      const stat = await Bun.file(join(env.home, '.joyhub', 'credentials.json')).stat()
      expect(stat?.mode && (stat.mode & 0o777)).toBe(0o600)
    }
  })

  test('returns a structured timeout while authorization remains pending', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      deviceFlow: { expiresIn: 1.05, interval: 0, pendingPolls: 100 }
    })

    const result = await runCli(['auth', 'ensure', '--registry', registry.url, '--json'], {
      HOME: env.home,
      USERPROFILE: env.home,
      JOYHUB_NO_BROWSER: '1'
    })

    expect(result.exitCode).toBe(2)
    const errorLine = result.stderr.split('\n').at(-1) ?? ''
    expect(JSON.parse(errorLine)).toMatchObject({
      ok: false,
      message: 'device authorization timed out'
    })
    expect(registry.received.devicePolls).toBeGreaterThan(0)
  })

  test('deletes a revoked token and reauthenticates with device flow', async () => {
    const env = await createTempHome()
    const stateDir = join(env.home, '.joyhub')
    await mkdir(stateDir, { recursive: true })
    registry = await startFakeRegistry({
      token: 'jh_replacement',
      deviceFlow: { accessToken: 'jh_replacement' }
    })
    await writeFile(join(stateDir, 'config.json'), JSON.stringify({ registry: registry.url }))
    await writeFile(
      join(stateDir, 'credentials.json'),
      JSON.stringify({ tokens: { [registry.url]: 'jh_revoked' } })
    )
    await chmod(join(stateDir, 'credentials.json'), 0o600)

    const result = await runCli(['auth', 'ensure', '--json'], {
      HOME: env.home,
      USERPROFILE: env.home,
      JOYHUB_NO_BROWSER: '1'
    })

    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout).reauthenticated).toBe(true)
    expect(await Bun.file(join(stateDir, 'credentials.json')).json())
      .toMatchObject({ tokens: { [registry.url]: 'jh_replacement' } })
    expect(result.stdout + result.stderr).not.toContain('jh_revoked')
    expect(result.stdout + result.stderr).not.toContain('jh_replacement')
  })

  test('reuses a valid stored token without opening a browser', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'jh_valid' })
    const login = await runCli(
      ['login', '--registry', registry.url, '--token', 'jh_valid'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(login.exitCode).toBe(0)

    const result = await runCli(['auth', 'ensure', '--json'], {
      HOME: env.home,
      USERPROFILE: env.home,
      JOYHUB_NO_BROWSER: '1'
    })

    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout).reauthenticated).toBe(false)
    expect(result.stderr).toBe('')
  })
})
