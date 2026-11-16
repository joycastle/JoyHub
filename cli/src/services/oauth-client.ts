import { createHash, randomBytes } from 'node:crypto'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export const OAUTH_SCOPE = 'resource:read skill:read skill:publish offline_access'

export interface OAuthServerMetadata {
  issuer?: string
  authorizationEndpoint: string
  tokenEndpoint: string
  registrationEndpoint: string
  revocationEndpoint: string
}

export interface OAuthClientRegistration {
  clientId: string
  clientSecret: string
  tokenEndpointAuthMethod: string
  scope: string
}

export interface OAuthTokenSet {
  accessToken: string
  expiresIn: number
  refreshToken: string
  scope: string
  tokenType: string
}

export interface OAuthCredentials {
  clientId: string
  clientSecret: string
  refreshToken: string
  expiresAt: number
  scope: string
  tokenType: string
}

export class OAuthTokenError extends Error {
  constructor(
    message: string,
    readonly errorCode: string | undefined,
    readonly httpStatus: number | undefined
  ) {
    super(message)
    this.name = 'OAuthTokenError'
  }
}

interface OAuthClientOptions {
  fetchImpl?: typeof fetch
}

export async function discoverOAuthServer(
  registry: string,
  options: OAuthClientOptions = {}
): Promise<OAuthServerMetadata | undefined> {
  const fetchImpl = options.fetchImpl ?? fetch
  let response: Response
  try {
    response = await fetchImpl(`${registry}/.well-known/oauth-authorization-server`, { signal: AbortSignal.timeout(30_000), redirect: 'error' })
  } catch {
    throw new CliError('registry unreachable', EXIT.network, {
      registry,
      next: 'check network or pass --registry'
    })
  }

  if (response.status === 404 || response.status === 405 || response.status === 501) {
    return undefined
  }

  if (!response.ok) {
    throw new CliError(`OAuth discovery failed with status ${response.status}`, response.status >= 500 ? EXIT.network : EXIT.auth, {
      registry,
      next: 'check the registry OAuth configuration'
    }, response.status)
  }

  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw oauthProtocolError(registry, 'OAuth discovery returned invalid JSON')
  }

  if (!isRecord(body)) {
    throw oauthProtocolError(registry, 'OAuth discovery returned an invalid document')
  }

  const authorizationEndpoint = resolveEndpoint(registry, body.authorization_endpoint)
  const tokenEndpoint = resolveEndpoint(registry, body.token_endpoint)
  const registrationEndpoint = resolveEndpoint(registry, body.registration_endpoint)
  const revocationEndpoint = resolveEndpoint(registry, body.revocation_endpoint)
  if (body.issuer !== registry) throw oauthProtocolError(registry, 'OAuth issuer does not match registry')
  const methods = body.code_challenge_methods_supported

  if (!authorizationEndpoint || !tokenEndpoint || !registrationEndpoint || !revocationEndpoint ||
      !Array.isArray(methods) || !methods.includes('S256')) {
    throw oauthProtocolError(registry, 'OAuth discovery is missing required PKCE endpoints')
  }

  return {
    ...(typeof body.issuer === 'string' ? { issuer: body.issuer } : {}),
    authorizationEndpoint,
    tokenEndpoint,
    registrationEndpoint,
    revocationEndpoint
  }
}

export async function registerOAuthClient(
  endpoint: string,
  redirectUri: string,
  options: OAuthClientOptions = {}
): Promise<OAuthClientRegistration> {
  const fetchImpl = options.fetchImpl ?? fetch
  const body = {
    client_name: 'JoyHub CLI',
    redirect_uris: [redirectUri],
    grant_types: ['authorization_code', 'refresh_token'],
    response_types: ['code'],
    token_endpoint_auth_method: 'client_secret_post',
    scope: OAUTH_SCOPE
  }

  const response = await fetchOAuthJson(fetchImpl, endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }, 'OAuth client registration failed')
  const data = unwrapOAuthBody(response)

  const clientId = stringField(data, 'client_id')
  const clientSecret = stringField(data, 'client_secret')
  const tokenEndpointAuthMethod = stringField(data, 'token_endpoint_auth_method')
  const scope = stringField(data, 'scope')
  if (!clientId || !clientSecret || tokenEndpointAuthMethod !== 'client_secret_post' || !scope || scope.split(/\s+/).sort().join(' ') !== OAUTH_SCOPE.split(' ').sort().join(' ')) {
    throw oauthProtocolError(new URL(endpoint).origin, 'OAuth registration returned invalid client metadata')
  }

  return { clientId, clientSecret, tokenEndpointAuthMethod, scope }
}

export async function exchangeOAuthCode(
  metadata: OAuthServerMetadata,
  registration: OAuthClientRegistration,
  redirectUri: string,
  code: string,
  codeVerifier: string,
  resource: string,
  options: OAuthClientOptions = {}
): Promise<OAuthTokenSet> {
  return requestToken(metadata.tokenEndpoint, new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    code_verifier: codeVerifier,
    redirect_uri: redirectUri,
    client_id: registration.clientId,
    client_secret: registration.clientSecret,
    resource
  }), options)
}

export async function refreshOAuthToken(
  registry: string,
  credentials: OAuthCredentials,
  options: OAuthClientOptions = {}
): Promise<OAuthTokenSet> {
  const metadata = await discoverOAuthServer(registry, options)
  if (!metadata) {
    throw new OAuthTokenError('OAuth token refresh is unsupported by this registry', 'unsupported', 404)
  }

  return requestToken(metadata.tokenEndpoint, new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: credentials.refreshToken,
    client_id: credentials.clientId,
    client_secret: credentials.clientSecret,
    resource: resourceForRegistry(registry)
  }), options)
}

export async function revokeOAuthToken(
  metadata: OAuthServerMetadata,
  credentials: OAuthCredentials,
  resource: string,
  options: OAuthClientOptions = {}
): Promise<void> {
  const fetchImpl = options.fetchImpl ?? fetch
  const response = await fetchOAuthResponse(fetchImpl, metadata.revocationEndpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      token: credentials.refreshToken,
      token_type_hint: 'refresh_token',
      client_id: credentials.clientId,
      client_secret: credentials.clientSecret,
      resource
    }).toString()
  }, 'OAuth token revocation failed')

  if (!response.ok) {
    throw await oauthResponseError(response, 'OAuth token revocation failed')
  }
}

export function createPkcePair(): { state: string; codeVerifier: string; codeChallenge: string } {
  const codeVerifier = randomBytes(48).toString('base64url')
  const codeChallenge = createHash('sha256').update(codeVerifier).digest('base64url')
  return {
    state: randomBytes(32).toString('base64url'),
    codeVerifier,
    codeChallenge
  }
}

export function resourceForRegistry(registry: string): string {
  return `${registry}/mcp`
}

async function requestToken(
  endpoint: string,
  form: URLSearchParams,
  options: OAuthClientOptions
): Promise<OAuthTokenSet> {
  const fetchImpl = options.fetchImpl ?? fetch
  const response = await fetchOAuthResponse(fetchImpl, endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString()
  }, 'OAuth token request failed')

  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new OAuthTokenError('OAuth token endpoint returned invalid JSON', undefined, response.status)
  }

  if (!response.ok) {
    const errorCode = isRecord(body) && typeof body.error === 'string' ? body.error : undefined
    throw new OAuthTokenError(
      errorCode ? `OAuth token request failed: ${errorCode}` : `OAuth token request failed with status ${response.status}`,
      errorCode,
      response.status
    )
  }

  const data = unwrapOAuthBody(body)
  const accessToken = stringField(data, 'access_token')
  const refreshToken = stringField(data, 'refresh_token')
  const tokenType = stringField(data, 'token_type')
  const scope = stringField(data, 'scope')
  const expiresIn = numberField(data, 'expires_in')
  if (!accessToken || !refreshToken || !tokenType || !scope || expiresIn === undefined || expiresIn < 0) {
    throw new OAuthTokenError('OAuth token endpoint returned an invalid token response', undefined, response.status)
  }

  return { accessToken, expiresIn, refreshToken, scope, tokenType }
}

async function fetchOAuthJson(
  fetchImpl: typeof fetch,
  endpoint: string,
  init: RequestInit,
  fallback: string
): Promise<unknown> {
  const response = await fetchOAuthResponse(fetchImpl, endpoint, init, fallback)
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw oauthProtocolError(new URL(endpoint).origin, `${fallback}: invalid JSON`)
  }
  if (!response.ok) {
    throw await oauthResponseError(response, fallback, body)
  }
  return body
}

async function fetchOAuthResponse(
  fetchImpl: typeof fetch,
  endpoint: string,
  init: RequestInit,
  fallback: string
): Promise<Response> {
  try {
    return await fetchImpl(endpoint, { ...init, signal: AbortSignal.timeout(30_000), redirect: 'error' })
  } catch {
    throw new CliError(`${fallback}: registry unreachable`, EXIT.network, {
      registry: new URL(endpoint).origin,
      next: 'check network or pass --registry'
    })
  }
}

async function oauthResponseError(response: Response, fallback: string, parsedBody?: unknown): Promise<OAuthTokenError | CliError> {
  let body = parsedBody
  if (body === undefined) {
    try {
      body = await response.json()
    } catch {
      body = undefined
    }
  }

  const errorCode = isRecord(body) && typeof body.error === 'string' ? body.error : undefined
  const message = errorCode ? `${fallback}: ${errorCode}` : `${fallback} with status ${response.status}`
  if (fallback === 'OAuth token request failed') {
    return new OAuthTokenError(message, errorCode, response.status)
  }
  return new CliError(message, response.status >= 500 ? EXIT.network : EXIT.auth, {
    registry: new URL(response.url || 'http://registry.invalid').origin,
    next: 'check the registry OAuth configuration'
  }, response.status)
}

function unwrapOAuthBody(body: unknown): Record<string, unknown> {
  if (!isRecord(body)) return {}
  if (isRecord(body.data)) return body.data
  return body
}

function resolveEndpoint(registry: string, value: unknown): string | undefined {
  if (typeof value !== 'string' || value.trim() === '') return undefined
  try {
    const url = new URL(value, `${registry}/`)
    if (url.origin !== new URL(registry).origin || url.username || url.password || url.hash) return undefined
    return url.toString().replace(/\/$/, '')
  } catch {
    return undefined
  }
}

function oauthProtocolError(registry: string, message: string): CliError {
  return new CliError(message, EXIT.auth, {
    registry,
    next: 'check the registry OAuth configuration'
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringField(record: Record<string, unknown>, field: string): string | undefined {
  return typeof record[field] === 'string' && record[field] ? record[field] : undefined
}

function numberField(record: Record<string, unknown>, field: string): number | undefined {
  return typeof record[field] === 'number' && Number.isFinite(record[field]) ? record[field] : undefined
}
