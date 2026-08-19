import { stat, readFile } from 'node:fs/promises'
import { basename } from 'node:path'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { JoyHubClient } from '../clients/skillhub-client'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { createZip, isZipFile, listZipFiles } from '../platform/archive'

export interface PublishCommandOptions {
  namespace?: string
  visibility?: string
  registry?: string
  token?: string
  json?: boolean
  dryRun?: boolean
}

export async function publishCommand(path: string, options: PublishCommandOptions): Promise<string> {
  // Validate local path
  let pathStat
  try {
    pathStat = await stat(path)
  } catch {
    throw new CliError(`path not found: ${path}`, EXIT.filesystem, { path })
  }

  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const namespace = options.namespace ?? 'global'
  const visibility = options.visibility ?? 'public'

  if (!token) {
    throw new CliError('authentication required for publish', EXIT.auth, { next: 'run `joyhub auth ensure`' })
  }

  // Create or read archive
  let archiveBlob: Blob
  let archiveName: string
  if (pathStat.isFile()) {
    if (await isZipFile(path)) {
      const buffer = await readFile(path)
      archiveBlob = new Blob([buffer], { type: 'application/zip' })
      archiveName = basename(path)
    } else {
      throw new CliError(`file must be a zip archive: ${path}`, EXIT.filesystem, { path })
    }
  } else if (pathStat.isDirectory()) {
    archiveBlob = await createZip(path)
    archiveName = `${basename(path)}.zip`
  } else {
    throw new CliError(`path must be a file or directory: ${path}`, EXIT.filesystem, { path })
  }

  const client = new JoyHubClient(registry, token)
  const files = await listZipFiles(archiveBlob)

  if (options.dryRun) {
    const result = await client.validatePublish(namespace, archiveBlob, toServerVisibility(visibility), archiveName)

    if (options.json) {
      if (!result.valid) {
        process.stdout.write(JSON.stringify({ ok: false, ...result, namespace, files }) + '\n')
        throw new CliError('validation failed', EXIT.validation)
      }
      return JSON.stringify({ ok: true, ...result, namespace, files })
    }

    const lines: string[] = []
    if (result.valid) {
      lines.push('Validation passed')
    } else {
      lines.push('Validation failed')
    }
    lines.push(`  Namespace: ${namespace}`)
    lines.push('  Files:')
    for (const file of files) {
      lines.push(`    - ${file}`)
    }
    if (result.resolvedSlug) {
      lines.push(`  Slug: ${result.resolvedSlug}`)
    }
    if (result.resolvedVersion) {
      lines.push(`  Version: ${result.resolvedVersion}`)
    }
    if (result.errors.length > 0) {
      lines.push('Errors:')
      for (const error of result.errors) {
        lines.push(`  - ${error}`)
      }
    }
    if (result.warnings.length > 0) {
      lines.push('Warnings:')
      for (const warning of result.warnings) {
        lines.push(`  - ${warning}`)
      }
    }
    if (!result.valid) {
      process.stdout.write(lines.join('\n') + '\n')
      throw new CliError('validation failed', EXIT.validation)
    }
    return lines.join('\n')
  }

  const result = await client.publish(namespace, archiveBlob, toServerVisibility(visibility), archiveName)

  const detailUrl = `${registry}/space/${result.namespace}/${encodeURIComponent(result.slug)}`

  if (options.json) {
    return JSON.stringify({
      ok: true,
      namespace: result.namespace,
      slug: result.slug,
      coordinate: `@${result.namespace}/${result.slug}`,
      version: result.version,
      status: result.status,
      visibility: result.visibility.toLowerCase(),
      detailUrl
    })
  }
  return `Published successfully: @${result.namespace}/${result.slug}@${result.version} (${result.status})\nDetail: ${detailUrl}`
}

/**
 * Convert kebab-case visibility to UPPER_SNAKE_CASE for server enum.
 */
function toServerVisibility(visibility: string): string {
  return visibility.toUpperCase().replace(/-/g, '_')
}
