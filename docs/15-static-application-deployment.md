# JoyHub 静态应用发布

JoyHub 是内部静态应用发布的控制面。部署 Runner 是独立的顶层应用，也是唯一允许写入版本目录或切换当前版本符号链接的组件。

```text
Catalog 资源 + ZIP 产物
        |
        v
JoyHub Server -- 带认证的 HTTP --> deployment-runner
        |                               |
        | 状态与审计                    | 不可变版本 + 原子符号链接
        v                               v
PostgreSQL                      static-host（只读 Nginx）
```

## P0 行为

- 一个可部署应用与一个现有 `CatalogResource` 一对一关联。
- 只有 Catalog 资源维护者或超级管理员可以创建和操作部署。
- 当前只支持 `STATIC` 部署模式。
- 创建版本时会快照当前 Catalog ZIP、记录 SHA-256，并携带共享 Bearer Token 同步调用 Runner 的 `/internal/v1/static/*` 接口。
- Runner 会先校验并解压候选版本，再切换 `published/{slug}`。符号链接采用原子替换；Static Host 验证失败时会恢复原链接。
- 发布成功后，控制面在同一事务中更新当前版本、稳定 URL、Catalog 版本和 Catalog 状态；发布失败只记录失败状态，不影响线上旧版本。
- 回滚和恢复只能选择已经保留的版本。下架只移除发布链接，保留 slug、稳定 URL 和所有版本目录。

P0 明确不负责源码构建、运行用户命令、接收 Compose 或 Nginx 片段、部署服务容器、管理 DNS/HTTPS，也不会修改共享公网入口。

## 本地启动

先启动 JoyHub 常规依赖和 Server，再启动部署执行面：

```bash
make dev
make deployment-up
make dev-server
```

容器化的 PR 前验证可以使用 `make staging`。该命令会构建并启动 Server、Runner 和 Static Host，并执行平台基础 smoke。需要验证完整部署生命周期时，再运行 `make deployment-smoke`。

本地访问地址：

- JoyHub Server：`http://localhost:8080`
- 部署 Runner 健康检查：`http://localhost:8091/actuator/health`
- Static Host：`http://localhost:8090/apps/{slug}/`

只停止部署执行面、保留已经发布的版本：

```bash
make deployment-down
```

## 用户发布流程

JoyHub 的 Catalog 发布按钮是普通维护者的统一入口。平台托管静态应用时，前端先保存草稿并上传 ZIP，随后调用 `POST /api/v1/catalog/resources/{slug}/publish`；Server 会自动创建（或复用）可部署应用、生成不可变 Release、调用 Runner 并在成功后发布 Catalog。调用方不需要手动串联部署 API。

1. 在在线工具表单中选择“平台托管静态应用”，填写版本并上传包含根目录 `index.html` 的 ZIP。
2. 点击“发布并自动部署”。首次发布按“创建草稿 → 上传产物 → 发布部署”执行。
3. 已发布应用上传新 ZIP 并填写新版本后，保存操作会自动发布新 Release；失败时保留旧版本在线。
4. 在 Catalog 执行下架时，Server 自动调用 Runner 移除发布链接并同步 Catalog 状态。

外部链接工具不创建部署实体，仍按普通 Catalog 流程发布。`访问入口` 由维护者填写；平台托管模式的稳定 URL 则由 Server 在部署成功后自动生成。

## 运维 API

`/api/v1/deployable-applications/**` 是回滚、恢复、问题定位等运维能力。管理员可以通过 `GET /api/v1/deployable-applications/{id}` 查看 Release 和操作记录，通过 `/rollback`、`/offline`、`/restore` 执行显式运维动作；它们不是普通发布表单的必经步骤。

所有面向用户的写接口都沿用 JoyHub 既有的 Session 与 CSRF 校验规则。

## 配置

JoyHub Server：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `JOYHUB_DEPLOYMENT_RUNNER_BASE_URL` | `http://localhost:8091` | Runner 内部地址 |
| `JOYHUB_DEPLOYMENT_RUNNER_TOKEN` | 本地开发 Token | Server 与 Runner 之间的共享凭据 |
| `JOYHUB_DEPLOYMENT_PUBLIC_ORIGIN` | `http://localhost:8090` | 写入 Catalog 的稳定 URL 来源 |
| `JOYHUB_DEPLOYMENT_PATH_PREFIX` | `/apps` | 稳定应用路径前缀 |
| `JOYHUB_DEPLOYMENT_CONNECT_TIMEOUT` | `PT5S` | 连接 Runner 的超时时间 |
| `JOYHUB_DEPLOYMENT_READ_TIMEOUT` | `PT60S` | 等待 Runner 响应的超时时间 |

Runner：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `JOYHUB_RUNNER_TOKEN` | 本地开发 Token | Runner 要求的 Bearer Token |
| `JOYHUB_RUNNER_DATA_PATH` | `/data` | 工作目录、版本目录和发布链接的根目录 |
| `JOYHUB_STATIC_VERIFICATION_ORIGIN` | `http://static-host` | Static Host 私网地址 |
| `JOYHUB_STATIC_PATH_PREFIX` | `/apps` | 私网验证路径前缀 |
| `JOYHUB_STATIC_MAX_ZIP_SIZE` | 50 MiB | ZIP 压缩包大小上限 |
| `JOYHUB_STATIC_MAX_EXPANDED_SIZE` | 200 MiB | 解压后总大小上限 |
| `JOYHUB_STATIC_MAX_SINGLE_FILE_SIZE` | 20 MiB | 单文件大小上限 |
| `JOYHUB_STATIC_MAX_FILE_COUNT` | 2,000 | 文件数量上限 |
| `JOYHUB_STATIC_MAX_COMPRESSION_RATIO` | 100 | 压缩炸弹防护阈值 |

非本地环境必须更换共享 Token，并将 Runner 限制在回环地址或私有容器网络中。Static Host 对共享部署卷仅拥有只读权限。

## 验证

运行 Runner 单元测试和安全测试：

```bash
make test-runner
```

运行完整控制面测试：

```bash
make test-backend-app
```

在本地服务和部署执行面运行期间，复现 Catalog 一键发布/更新/下架，以及底层 v1/v2 发布、回滚、坏包隔离、恢复、非维护者拒绝、Runner Token 拒绝和 Runner 重启恢复：

```bash
make deployment-smoke
```
