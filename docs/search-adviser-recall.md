# Search and AI adviser retrieval

Ordinary search remains local: Jieba query terms, literal field matching, and the bundled BGE-small-zh semantic model. Exact queries retain priority. A small task vocabulary adds bounded alternatives for image generation, video production, reports, presentations, and charts. The alternatives describe tasks rather than naming or privileging particular resources. Semantic qualification thresholds are unchanged; low similarity alone does not become a recommendation.

The unified projection reads capability objects through their `value` field, alongside legacy string capabilities and generated search terms. Verified capabilities therefore participate in high-signal matching instead of being silently discarded as malformed string arrays. Indexed Skill candidates retain their actual download/star quality contribution. Stored profiles and vectors do not need regeneration.

The shared query interpreter recognizes whole English constraint words, so `OpenAI` does not imply OPEN access and `SkillHub` does not imply SKILL type. Negated Chinese access mentions do not become positive filters. Explicitly rejected resource types are removed from candidates, including follow-ups such as “不是这些skill”. Explicit user scope and visibility are applied before ranking. The adviser uses this same interpretation of the human request; words introduced by model-generated search queries cannot create additional hard resource/access filters.

When AI is configured, the adviser first uses the existing model planner to resolve conversation references and propose up to five steps with up to four short bilingual queries each. Planning has a maximum 12-second request timeout, low reasoning effort, and a 1,536-token output cap. A planning failure retains deterministic local planning. Answer generation and its optional model fallback share a 65-second request budget, leaving time inside the existing 100-second browser timeout for planning and retrieval. Configured shorter request timeouts remain effective.

Per-query results are deduplicated using their best relevance score before the per-step candidate limit is applied. Up to three relevant paragraphs from the profile and original documentation replace raw capability JSON as model evidence. The answer may reference only candidates supplied for that step. If the model rejects every candidate, its empty result is respected instead of forcibly reintroducing weak matches. Existing deterministic guidance remains available when the answer call fails.

## Validation

Unit/regression coverage includes colloquial task recall, unrelated queries, chart/image disambiguation, positive and negated type/access constraints, whole-word English constraints, structured capability matching, generated-query scope isolation, multi-query ordering, bounded planner requests, planner failure recovery, and rejection of unknown resource references.

On 2026-11-10 a read-only snapshot of 138 published public/global resources was evaluated using the bundled BGE model and current search classes. This is a focused acceptance corpus, not an exhaustive relevance benchmark:

| Query | Expected result observed |
|---|---|
| 画图 | AI Imagine CLI / MCP within the leading results |
| 做视频 | 视频制作 first; AI Imagine also recalled |
| 整理报表 | 每周HTML报告生成 and XLSX广告报表转换 recalled |
| 做PPT | 横向网页PPT and 动态网页幻灯片 lead |
| 剪辑视频 | 视频制作 recalled |
| 生成音乐 | AudioCraft音频生成 and HeartMuLa歌曲生成 recalled |
| 写周报 | Weekly-report resources recalled |
| 火星土壤检测 | No ordinary-search result |

A temporary, non-persistent probe used the production relay, `gpt-6.1-terra`, public resource metadata, the revised planner/client, and the revised ranking/evidence code. It did not alter the running application or database. Observed planner-to-answer durations were approximately 7–10 seconds:

- “我想画图，能用什么”: AI Imagine selected first, with related alternatives.
- “我想做一个视频”: 视频制作 selected first, with AI Imagine as an alternative.
- “整理报表，生成可分享的网页报告”: separate preparation/output steps, with data-analysis/report resources selected. Domain-specific UA Skills were not treated as general reporting guarantees.
- “火星土壤检测”: the answer identified the capability gap and selected no resource despite broad rewritten-query candidates.

Resource titles, aliases, and evidence still affect ranking. Not every phrase is covered by the deterministic task vocabulary; model query rewriting broadens adviser retrieval without claiming to improve every ordinary-search query. No categories, HTTP contracts, credentials, or visibility rules are changed.
