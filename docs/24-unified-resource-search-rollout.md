# 统一资源搜索上线说明

## 目标

Skill、Agent 和 Tool 的业务表保持各自的扩展字段；搜索只读取一张
`resource_search_document` 投影表。发布或更新后先同步基础文档，随后异步生成有证据的
搜索画像。因此 AI 生成失败不会阻塞资源发布或基础搜索。

## 数据库迁移

迁移文件为：

`server/skillhub-app/src/main/resources/db/migration/V55__unified_resource_search_documents.sql`

应用的默认配置已启用 Flyway（`spring.flyway.enabled=true`）。部署包含该迁移的后端版本后，
应用启动时会在同一数据库事务序列中执行 V55：

1. 创建 `resource_search_document`、唯一约束和全文索引；
2. 为现有可搜索 Skill、已发布 Agent 和 Tool 写入基础搜索文档；
3. 将这些文档标记为 `PENDING`，后台任务再生成搜索画像。

Flyway 会将 V55 记录到 `flyway_schema_history`，同一个数据库只执行一次。不要在线上手工执行
SQL，也不要手工插入 Flyway 历史记录。

## 上线步骤

1. 先对生产 PostgreSQL 做可恢复备份，并在与生产同版本的预发数据库完成迁移演练。
2. 合并/拉取包含 V55 的 `main`，构建并发布新的后端镜像。
3. 使用正常发布流程滚动重启后端。启动日志应出现 Flyway 的 V55 成功记录；不要关闭
   `spring.flyway.enabled`。
4. 等待后台画像任务处理 `PENDING` 文档；管理员可在“管理员 → 搜索画像”查看状态并对单条
   资源重新生成。
5. 用下方 SQL 核验迁移和数据，再用验收查询核对网页、CLI/王总和 AI 顾问的结果一致性。

## 生产核验 SQL

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version = '55';

SELECT resource_type, generation_status, count(*)
FROM resource_search_document
GROUP BY resource_type, generation_status
ORDER BY resource_type, generation_status;

SELECT resource_type, resource_id, title, generation_status, updated_at
FROM resource_search_document
ORDER BY updated_at DESC
LIMIT 20;
```

`PENDING` 是刚迁移或刚发布后的正常短暂状态；长期 `FAILED` 不影响资源使用，但应由管理员打开
搜索画像页面查看原因并重新生成。

## 回滚边界

V55 只新增表、索引和搜索投影，不修改既有资源业务表。应用代码回滚时不需要删除新表；保留它
即可。若必须恢复数据库，按发布前的数据库备份执行，不要对生产库执行 `DROP TABLE`。
