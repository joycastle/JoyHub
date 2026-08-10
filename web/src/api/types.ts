import type { components } from './generated/schema'

export type User = Omit<components['schemas']['AuthMeResponse'], 'userId' | 'displayName' | 'platformRoles'> & {
  userId: string
  displayName: string
  email?: string
  avatarUrl?: string
  oauthProvider?: string
  platformRoles: string[]
}

export type OAuthProvider = Omit<components['schemas']['AuthProviderResponse'], 'id' | 'name' | 'authorizationUrl'> & {
  id: string
  name: string
  authorizationUrl: string
}

export interface AuthMethod {
  id: string
  methodType: 'PASSWORD' | 'OAUTH_REDIRECT' | 'DIRECT_PASSWORD' | 'SESSION_BOOTSTRAP' | string
  provider: string
  displayName: string
  actionUrl: string
}

export interface ApiToken {
  id: number
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt?: string
  lastUsedAt?: string
}

export interface CreateTokenRequest {
  name: string
  scopes?: string[]
  expiresAt?: string
}

export interface CreateTokenResponse {
  token: string
  id: number
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt?: string
}

export interface LocalLoginRequest {
  username: string
  password: string
}

export interface LocalRegisterRequest extends LocalLoginRequest {
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface PasswordResetRequest {
  email: string
}

export interface PasswordResetConfirmRequest {
  email: string
  code: string
  newPassword: string
}

export type CreateNamespaceRequest = Omit<components['schemas']['NamespaceRequest'], 'slug' | 'displayName'> & {
  slug: string
  displayName: string
  description?: string
}

export interface MergeInitiateRequest {
  secondaryIdentifier: string
}

export interface MergeInitiateResponse {
  mergeRequestId: number
  secondaryUserId: string
  verificationToken: string
  expiresAt: string
}

export interface MergeVerifyRequest {
  mergeRequestId: number
  verificationToken: string
}

export interface MergeConfirmRequest {
  mergeRequestId: number
}

// Namespace types
export type NamespaceStatus = 'ACTIVE' | 'FROZEN' | 'ARCHIVED' | string
export type NamespaceRole = 'OWNER' | 'ADMIN' | 'MEMBER' | string

export interface Namespace {
  id: number
  slug: string
  displayName: string
  description?: string
  type: 'GLOBAL' | 'TEAM'
  avatarUrl?: string
  status: NamespaceStatus
  createdAt: string
  updatedAt?: string
}

export interface ManagedNamespace extends Namespace {
  createdBy?: string
  currentUserRole?: NamespaceRole
  immutable: boolean
  canFreeze: boolean
  canUnfreeze: boolean
  canArchive: boolean
  canRestore: boolean
  canDelete: boolean
}

export interface SkillRepository {
  slug: string
  displayName: string
  defaultRepository: boolean
}

export interface NamespaceMember {
  id: number
  userId: string
  displayName?: string
  email?: string
  role: NamespaceRole
  createdAt: string
}

export interface NamespaceCandidateUser {
  userId: string
  displayName: string
  email?: string
  status: string
}

export interface BatchMemberResult {
  userId: string
  role: string
  success: boolean
  error?: string
}

export interface BatchMemberResponse {
  totalCount: number
  successCount: number
  failureCount: number
  results: BatchMemberResult[]
}

// JoyHub catalog types. The stricter aliases keep required API fields usable in feature code while
// the generated OpenAPI model remains the source of truth for the wire contract.
export type CatalogResourceKind =
  | 'AGENT'
  | 'PLUGIN'
  | 'MCP_SERVER'
  | 'ONLINE_TOOL'
  | 'INTERNAL_SERVICE'
  | 'KNOWLEDGE_BASE'
  | 'TEMPLATE'
  | 'RESOURCE_PACK'

export type CatalogCenter = 'AGENT' | 'TOOL'
export type CatalogResourceStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'ARCHIVED'
export type CatalogMaintenanceStatus = 'ACTIVE' | 'MAINTENANCE' | 'DEPRECATED'
export type CatalogVisibilityScope = 'COMPANY' | 'DEPARTMENTS'

export type CatalogDepartment = components['schemas']['CatalogDepartmentResponse']
export type CatalogOwner = components['schemas']['CatalogOwnerResponse']
export type CatalogRelatedSkill = components['schemas']['CatalogRelatedSkillResponse']

export type CatalogResourceSummary = Omit<
  components['schemas']['CatalogResourceSummaryResponse'],
  'id' | 'slug' | 'name' | 'summary' | 'kind'
> & {
  id: number
  slug: string
  name: string
  summary: string
  kind: CatalogResourceKind
  status?: CatalogResourceStatus
  maintenanceStatus?: CatalogMaintenanceStatus
  visibilityScope?: CatalogVisibilityScope
}

export type UnifiedResourceSearchType = 'ALL' | 'AGENT' | 'TOOL' | 'SKILL'

export type UnifiedResourceSearchItem = Omit<
  components['schemas']['UnifiedResourceSearchItemResponse'],
  'resourceType' | 'accessMode' | 'relevanceScore' | 'skill' | 'catalogResource'
> & {
  resourceType: Exclude<UnifiedResourceSearchType, 'ALL'>
  accessMode: 'INSTALL' | 'OPEN' | 'DOWNLOAD' | string
  relevanceScore: number
  skill?: SkillSummary
  catalogResource?: CatalogResourceSummary
}

export type CatalogResourceDetail = Omit<
  components['schemas']['CatalogResourceDetailResponse'],
  | 'id'
  | 'slug'
  | 'name'
  | 'summary'
  | 'kind'
  | 'visibleDepartments'
  | 'relatedResources'
  | 'relatedSkills'
> & CatalogResourceSummary & {
  documentation?: string
  visibleDepartments?: CatalogDepartment[]
  relatedResources?: CatalogResourceSummary[]
  relatedSkills?: CatalogRelatedSkill[]
  agentUsageBoundary?: string
  agentInputGuide?: string
  agentOutputGuide?: string
  agentSupportContact?: string
  agentExamplePrompts?: string[]
  artifactFilename?: string
  artifactSize?: number
  canManage?: boolean
}

// Keep the hand-written facade forward-compatible while the checked-in OpenAPI schema catches up
// with the catalog agent profile fields introduced by the backend.
export type CatalogResourceRequest = components['schemas']['CatalogResourceRequest'] & {
  agentUsageBoundary?: string
  agentInputGuide?: string
  agentOutputGuide?: string
  agentSupportContact?: string
  agentExamplePrompts?: string[]
}

export type DiscoveryAssistRequest = components['schemas']['DiscoveryAssistRequest']

export type DiscoverySuggestionResponse = Omit<
  components['schemas']['DiscoverySuggestionResponse'],
  'type' | 'id' | 'title' | 'description' | 'kind' | 'slug'
> & {
  type: 'catalog' | 'skill'
  id: number
  title: string
  description: string
  kind: string
  slug: string
  namespace?: string
  accessUrl?: string
  usage?: string
  evidence?: string
  source?: string
}

export interface DiscoveryPlanStepResponse {
  objective: string
  suggestions: DiscoverySuggestionResponse[]
}

export type DiscoveryAssistResponse = Omit<
  components['schemas']['DiscoveryAssistResponse'],
  'conversationId' | 'answer' | 'suggestions' | 'steps' | 'modelGenerated' | 'fallbackUsed'
> & {
  conversationId: string
  answer: string
  suggestions: DiscoverySuggestionResponse[]
  steps: DiscoveryPlanStepResponse[]
  modelGenerated: boolean
  fallbackUsed: boolean
  model?: string
}

// Skill types
export interface SkillSummary {
  id: number
  slug: string
  displayName: string
  summary?: string
  localizedDisplayName?: string
  localizedSummary?: string
  visibility?: string
  status?: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  namespace: string
  updatedAt: string
  canSubmitPromotion: boolean
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  resolutionMode?: string
}

export interface ResourceSummary {
  resourceId: string
  sourceType: 'SKILL' | 'CATALOG' | string
  sourceId: number
  kind: string
  slug: string
  name: string
  summary?: string
  namespace?: string
  status: string
  version?: string
  versionStatus?: string
  visibility?: string
  downloadCount: number
  starCount: number
  ratingCount: number
  canManage: boolean
  updatedAt: string
  actions: string[]
  favorited: boolean
}

export interface ResourceStats {
  resourceId: string
  viewCount: number
  useCount: number
  downloadCount: number
  favoriteCount: number
  favorited: boolean
}


export type LabelItem = Omit<components['schemas']['SkillLabelDto'], 'slug' | 'type' | 'displayName'> & {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED' | string
  displayName: string
}

export type LabelTranslation = Omit<components['schemas']['LabelTranslationResponse'], 'locale' | 'displayName'> & {
  locale: string
  displayName: string
}

export type LabelDefinition = Omit<
  components['schemas']['LabelDefinitionResponse'],
  'slug' | 'type' | 'translations' | 'sortOrder' | 'visibleInFilter'
> & {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED' | string
  visibleInFilter: boolean
  sortOrder: number
  translations: LabelTranslation[]
}

export interface AdminLabelInput {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED'
  visibleInFilter: boolean
  sortOrder: number
  translations: LabelTranslation[]
}

export interface SkillLifecycleVersion {
  id: number
  version: string
  status: string
}

export interface SkillDetail {
  id: number
  slug: string
  displayName: string
  ownerId?: string
  ownerDisplayName?: string
  summary?: string
  visibility: string
  status: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  hidden: boolean
  namespace: string
  labels?: LabelItem[]
  canManageLifecycle: boolean
  canSubmitPromotion: boolean
  canInteract: boolean
  canReport: boolean
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  ownerPreviewReviewComment?: string
  resolutionMode?: string
}

export interface SubmitPromotionRequest {
  sourceSkillId: number
  sourceVersionId: number
  targetNamespaceId: number
}

export interface SkillVersion {
  id: number
  version: string
  status: string
  changelog?: string
  fileCount: number
  totalSize: number
  publishedAt: string
  downloadAvailable: boolean
}

export interface SkillVersionDetail {
  id: number
  version: string
  status: string
  changelog?: string
  fileCount: number
  totalSize: number
  publishedAt: string
  parsedMetadataJson?: string
  manifestJson?: string
}

export interface SkillFile {
  id: number
  filePath: string
  fileSize: number
  contentType: string
  sha256: string
}

export interface SkillVersionCompareLine {
  type: 'CONTEXT' | 'ADD' | 'DELETE' | string
  content: string
  oldLineNumber: number | null
  newLineNumber: number | null
}

export interface SkillVersionCompareHunk {
  oldStart: number
  oldLines: number
  newStart: number
  newLines: number
  lines: SkillVersionCompareLine[]
}

export interface SkillVersionCompareFile {
  path: string
  changeType: 'ADDED' | 'MODIFIED' | 'REMOVED' | string
  oldSize: number | null
  newSize: number | null
  binary: boolean
  truncated: boolean
  hunks: SkillVersionCompareHunk[]
}

export interface SkillVersionCompareSummary {
  totalFiles: number
  addedFiles: number
  modifiedFiles: number
  removedFiles: number
  addedLines: number
  removedLines: number
}

export interface SkillVersionCompare {
  from: string
  to: string
  summary: SkillVersionCompareSummary
  files: SkillVersionCompareFile[]
}

export interface SkillTag {
  id: number
  tagName: string
  versionId: number
  createdAt: string
}

// Search and pagination
export interface SearchParams {
  q?: string
  namespace?: string
  label?: string
  sort?: string
  page?: number
  size?: number
  starredOnly?: boolean
}

export interface PagedResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// Publish
export interface PublishResult {
  skillId: number
  namespace: string
  slug: string
  version: string
  status: string
  fileCount: number
  totalSize: number
}

export interface BatchPublishItemResult {
  filename: string
  success: boolean
  needsConfirmation: boolean
  publish?: PublishResult | null
  errorCode?: string | null
  errorMessage?: string | null
  warnings?: string[]
}

export interface BatchPublishResult {
  total: number
  succeeded: number
  failed: number
  needsConfirmation: number
  items: BatchPublishItemResult[]
}

export interface SkillDeleteResult {
  skillId?: number
  namespace?: string
  slug?: string
  deleted?: boolean
}

export interface ReviewTask {
  id: number
  skillVersionId: number
  namespace: string
  skillSlug: string
  version: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedByName?: string
  reviewedBy?: string
  reviewedByName?: string
  reviewComment?: string
  submittedAt: string
  reviewedAt?: string
}

export interface ReviewSkillDetail {
  skill: SkillDetail
  versions: SkillVersion[]
  files: SkillFile[]
  documentationPath?: string
  documentationContent?: string
  downloadUrl: string
  activeVersion: string
}

export type PromotionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type PromotionSortDirection = 'ASC' | 'DESC'
export type PromotionSortBy = 'reviewedAt'

export interface PromotionTask {
  id: number
  sourceSkillId: number
  sourceSkillDisplayName: string
  sourceSkillSummary?: string | null
  sourceNamespace: string
  sourceSkillSlug: string
  sourceVersion: string
  sourceVersionFileCount: number
  sourceVersionTotalSize: number
  sourceSkillDownloadCount: number
  sourceSkillStarCount: number
  targetNamespace: string
  targetSkillId?: number | null
  status: PromotionStatus
  submittedBy: string
  submittedByName?: string | null
  reviewedBy?: string | null
  reviewedByName?: string | null
  reviewComment?: string | null
  submittedAt: string
  reviewedAt?: string | null
}

export interface SkillReport {
  id: number
  skillId: number
  namespace?: string
  skillSlug?: string
  skillDisplayName?: string
  reporterId: string
  reason: string
  details?: string
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED' | string
  handledBy?: string
  handleComment?: string
  createdAt: string
  handledAt?: string
}

export type ReportDisposition = 'RESOLVE_ONLY' | 'RESOLVE_AND_HIDE' | 'RESOLVE_AND_ARCHIVE'

export interface GovernanceSummary {
  pendingReviews: number
  pendingPromotions: number
  pendingReports: number
  unreadNotifications: number
}

export interface GovernanceInboxItem {
  type: 'REVIEW' | 'PROMOTION' | 'REPORT' | string
  id: number
  title: string
  subtitle?: string
  timestamp?: string
  namespace?: string
  skillSlug?: string
}

export interface GovernanceActivityItem {
  id: number
  action: string
  actorUserId?: string
  actorDisplayName?: string
  targetType?: string
  targetId?: string
  details?: string
  timestamp?: string
}

export interface GovernanceNotification {
  id?: number
  category: string
  entityType: string
  entityId: number
  title: string
  bodyJson?: string
  status: 'UNREAD' | 'READ' | string
  createdAt?: string
  readAt?: string
}

export interface AdminUser {
  userId: string
  username: string
  email?: string
  platformRoles: string[]
  status: string
  createdAt: string
}

export interface AuditLogItem {
  id: string
  userId?: string
  username?: string
  action: string
  details?: string
  requestId?: string
  resourceType?: string
  resourceId?: string
  timestamp: string
  ipAddress?: string
}

// Notification types
export interface NotificationItem {
  id: number
  category: 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'
  eventType: string
  title: string
  bodyJson?: string
  entityType?: string
  entityId?: number
  targetType?: string
  targetId?: number
  targetRoute?: string
  status: 'UNREAD' | 'READ'
  createdAt: string
  readAt?: string
}

export interface NotificationPreferenceItem {
  category: string
  channel: string
  enabled: boolean
}

export interface NotificationUnreadCount {
  count: number
}
