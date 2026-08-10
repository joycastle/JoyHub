# JoyHub 统一资源搜索架构

## 0 统一搜索边界

Skill、Agent、Tool 保持各自的业务聚合与生命周期，但必须投影成同一种轻量搜索文档，
并复用同一个查询理解和混合排序器：

```java
public record ResourceSearchDocument(
    String id,
    String resourceType,     // SKILL / AGENT / TOOL
    String title,
    String slug,
    String summary,
    List<String> scenarios,
    List<String> tags,
    String documentation,
    String accessMode,       // INSTALL / OPEN / DOWNLOAD
    String semanticVector,
    double qualityScore
) {}
```

不同入口只增加结构化条件，不得维护另一套相关性算法：

| 入口 | 资源范围 |
|------|---------|
| 全站搜索 | 当前用户可见的全部资源 |
| Agent / Tool / Skill 中心 | 对应资源类型 |
| AI 能力顾问 | 复用统一资源搜索池，再为命中项补充文档证据 |
| Agent 安装接口 | `resourceType=SKILL AND accessMode=INSTALL` |

Web 搜索页统一调用 `GET /api/web/resources/search`。该接口负责先完成 Skill、Agent、Tool
的权限过滤和轻量文档投影，再把所有候选一次性交给混合排序器，最后统一截断、分页和返回。
前端不得分别调用 Skill 搜索和 Catalog 搜索后自行拼接，也不得按资源类型分段展示“伪统一”结果。
`type=AGENT|TOOL|SKILL` 只是同一候选池上的硬过滤条件，`type=ALL` 才执行跨类型混排。
`starredOnly=true` 同样只是在权限过滤后的统一候选池上应用收藏关系过滤；前端不得切回
Skill 专用收藏列表，也不得自行过滤、排序或分页收藏结果。

统一响应中每一项都带有公共判别字段：

```text
resourceType + accessMode + relevanceScore + (skill | catalogResource)
```

其中载荷保持原业务摘要结构，避免搜索接口反向污染 Skill 或 Catalog 的生命周期模型。

AI 能力顾问不得重新读取 Skill 与 Catalog 后分别召回、排序和合并。它可以把用户需求拆成
多个检索表达，但每个表达都必须调用与 Web 搜索相同的 `UnifiedResourceSearchAppService`，
并在统一结果返回后仅补充 `SKILL.md`、配套文档或资源说明作为模型证据。模型负责选择、解释
和组织方案，不负责绕过统一候选池重新发明搜索结果。用户原始需求必须作为每个任务步骤的
首个检索表达，模型生成的步骤关键词只能补充召回，不能替代统一搜索入口收到的原始查询。
对于包含多个动作或对象的复合需求，AI 检索层可以自动生成更短的动作—产出、输入—对象
查询，但每个扩展查询仍必须逐一通过统一资源搜索池；不得维护产品别名表或旁路索引。

查询流程固定为：权限和生命周期过滤 → 精确/分词/语义并行召回 → 去重排序 →
按入口投影响应。语义检索必须扫描过滤后的候选语料，不能只重排关键词已经命中的结果。
当查询已存在标题、摘要、场景或标签的强词法命中时，纯语义候选必须同时满足更高的绝对阈值
和相对最优分数窗口；这用于阻止“生成报告”一类明确需求被通用文档噪声扩散成所有资源。

## 1 SPI 接口

```java
public interface SearchIndexService {
    void index(SkillSearchDocument doc);
    void batchIndex(List<SkillSearchDocument> docs);
    void remove(Long skillId);
}

public interface SearchQueryService {
    SearchResult search(SearchQuery query);
}

public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
```

## 2 SearchQuery 模型

```java
public record SearchQuery(
    String keyword,
    Long namespaceId,           // 可选，指定空间搜索
    String namespaceSlug,       // 可选
    SearchVisibilityScope scope, // ACL 投影，由应用服务层计算注入
    SortField sortBy,           // RELEVANCE / DOWNLOADS / RATING / NEWEST
    int page,
    int size
) {}

// 搜索可见范围投影，由应用服务层根据当前用户计算
public record SearchVisibilityScope(
    boolean includeAllPublic,        // 是否包含所有 PUBLIC 技能
    Set<Long> memberNamespaceIds,    // 用户是 MEMBER 的 namespace（可见 NAMESPACE_ONLY）
    Set<Long> adminNamespaceIds,     // 用户是 ADMIN 的 namespace（可见 PRIVATE）
    String userId                    // 当前用户 ID（可见自己的 PRIVATE skill），匿名为 null
) {}
```

ACL 投影计算规则：
- 匿名用户：`includeAllPublic=true`，其余为空集，`userId=null`
- 已登录用户：`includeAllPublic=true`，`memberNamespaceIds` = 用户所属空间，`adminNamespaceIds` = 用户是 ADMIN 以上的空间，`userId` = 当前用户 ID

一期 PostgreSQL 实现中，`SearchVisibilityScope` 转换为 WHERE 条件：
```sql
WHERE (visibility = 'PUBLIC')
   OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:memberNamespaceIds))
   OR (visibility = 'PRIVATE' AND (namespace_id IN (:adminNamespaceIds) OR owner_id = :userId))
```

迁移到 ES 时，`SearchVisibilityScope` 可直接映射为 bool query 的 should/filter 子句。

## 3 搜索文档表 skill_search_document

一个 skill 对应一条搜索文档，但文档内容的来源语义应严格收敛为“当前最新已发布版本”。实现上仍可由 `latest_version_id` 作为缓存指针承载，但它只允许指向 `PUBLISHED` 版本；搜索层不能再把它当作泛化的“当前版本”。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | 唯一，一 skill 一条 |
| namespace_id | bigint | 用于空间过滤 |
| owner_id | VARCHAR(128) | 用于 PRIVATE 可见性判定 |
| title | varchar(256) | |
| summary | varchar(512) | |
| keywords | varchar(512) | |
| search_text | text | `displayName`、`slug`、`summary`，以及 frontmatter 中除 `name` / `description` / `version` 外的字段展开结果 |
| visibility | enum | 冗余，避免搜索时 join |
| status | enum | |
| updated_at | datetime | |

唯一约束：`(skill_id)`

PostgreSQL 全文搜索索引：表增加 `search_vector tsvector` 生成列，基于 `title`、`summary`、`keywords`、`search_text` 自动维护，建立 GIN 索引。详见第 7 节。

## 4 索引写入时机

以下场景触发搜索文档更新（upsert by skill_id）：
- 审核通过（`PENDING_REVIEW → PUBLISHED`）：重算“最新已发布版本”指针，并用该发布版本内容更新搜索文档
- 已发布版本被撤回（`PUBLISHED → YANKED`）：重算“最新已发布版本”指针；若不存在任何已发布版本，则移除搜索文档
- 技能状态变更（隐藏/归档/恢复）：更新搜索文档的 status 字段

## 5 搜索演进路线

### 5.1 一期数据建模约束

一期“每个 skill 一条搜索文档、内容永远取最新已发布版本”是有意的简化。当前实现仍使用 `latest_version_id` 作为持久化指针，但这里的语义已经收敛为 latest published pointer。这个模型在以下场景下会不够用：

- 版本级检索（搜索某个旧版本的内容）
- 自定义标签/通道检索（搜索 `@beta` 标签指向的版本内容）
- 向量 chunk 索引（一个 skill 的 SKILL.md 拆成多个 embedding chunk）

这些场景不是简单换 provider 能解决的，需要改表结构和索引写入逻辑。

**一期搜索能力边界（产品限制）：**
- 搜索只基于“最新已发布版本”的内容
- 不支持按 version 或 tag 搜索内容
- 搜索结果不区分 channel（`beta`、`stable` 等标签通道）
- 用户通过 tag 安装的技能内容可能与搜索结果展示的内容不一致（搜索展示 latest，安装的是 tag 指向的版本）
- 若要支持 channel-aware 搜索，必须升级到 version 级索引（二期 ES 实现）

### 5.2 演进阶段

| 阶段 | 实现 | 索引粒度 | 切换方式 |
|------|------|---------|---------|
| 一期 | PostgreSQL Full-Text (tsvector + GIN) | 每 skill 一条（latest published） | 默认 |
| 一点五期 | PostgreSQL Full-Text + 有界语义召回 | 每 skill 一条（latest published） | 配置 `skillhub.search.semantic.enabled=true` |
| 二期 | ES / OpenSearch | 每 skill_version 一条 + skill 聚合文档 | 配置 `search.provider=elasticsearch` |
| 三期 | 向量检索 | 每 skill_version 多条（chunk 级） | 配置 `search.provider=vector` |
| 四期 | 混合排序 | 关键词 + 向量混合 | 配置 `search.provider=hybrid` |

当前代码实现已落在“一点五期”：
- PostgreSQL 先执行权限、生命周期、命名空间与可安装状态过滤
- 搜索文档表新增 `semantic_vector` 缓存字段
- 过滤后的有界候选集同时计算标题、分词和语义相关性，不要求候选先命中关键词
- Agent、Tool 使用同一个 `ResourceSearchDocument` 与混合排序器，不再维护字符串包含搜索
- 当前公司规模扫描上限为 500 个可见候选；达到规模上限前必须用真实查询集重新校准

### 5.3 SPI 演进策略

一期 SPI 接口（`SearchIndexService` / `SearchQueryService`）的入参是 `SkillSearchDocument`（skill 粒度）。二期切换到 ES 时：

1. 新增 `SkillVersionSearchDocument` 模型（version 粒度）
2. `SearchIndexService` 新增 `indexVersion()` 方法（向下兼容，一期实现空方法）
3. ES 实现同时写入 skill 聚合文档 + version 文档
4. `SearchQueryService.search()` 的返回结果不变（仍返回 skill 级摘要），内部实现切换为 ES 查询

这意味着二期切换不是零成本的——需要新增模型、扩展 SPI、重建索引。但一期不为此过度设计，SPI 抽象保证了切换时不需要改业务层代码。

通过 `@ConditionalOnProperty` 或自定义 SPI 加载机制切换。

## 6 分布式安全

`rebuildAll()` / `rebuildByNamespace()` 执行前获取 Redis 分布式锁（key: `search:rebuild:{scope}`，TTL: 10min），获取失败则跳过。

## 7 PostgreSQL 全文搜索中文支持

PostgreSQL 全文搜索使用 `tsvector` + `tsquery` + GIN 索引：

```sql
-- 增加 tsvector 生成列
ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

-- 建立 GIN 索引
CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
```

中文支持方案：
- 一期使用 `simple` 分词配置（按空格和标点分词），对中文支持有限但零依赖
- 如需更好的中文分词，可安装 `zhparser` 或 `pg_jieba` 扩展，替换为对应的 text search configuration
- PostgreSQL 的 `tsvector` 支持权重（A/B/C/D），可对 title 赋予更高权重，提升搜索相关性

已知局限：`simple` 分词对中文的精度不如专业搜索引擎。建议 Phase 2 完成后评估搜索效果，如不满足需求则在 Phase 3 提前引入 ES。
