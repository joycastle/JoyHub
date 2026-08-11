package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.catalog.domain.CatalogMaintenanceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceDraft;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently imports the Bingo Voyage project-tool catalog when explicitly enabled. */
@Component
@ConditionalOnProperty(name = "joyhub.catalog.seed.bingo-voyage.enabled", havingValue = "true")
@Order(101)
public class BingoVoyageCatalogInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BingoVoyageCatalogInitializer.class);
    private static final String SOURCE_PREFIX = "bingo-voyage-tools:";

    private final CatalogResourceRepository repository;
    private final NamespaceRepository namespaceRepository;
    private final Clock clock;
    private final String ownerId;
    private final String primaryNamespaceSlug;
    private final String visibilityDepartmentExternalId;

    public BingoVoyageCatalogInitializer(
            CatalogResourceRepository repository,
            NamespaceRepository namespaceRepository,
            Clock clock,
            @Value("${joyhub.catalog.seed.bingo-voyage.owner-id:local-admin}") String ownerId,
            @Value("${joyhub.catalog.seed.bingo-voyage.primary-namespace-slug:gestalt}")
            String primaryNamespaceSlug,
            @Value("${joyhub.catalog.seed.bingo-voyage.visibility-department-external-id:}")
            String visibilityDepartmentExternalId
    ) {
        this.repository = repository;
        this.namespaceRepository = namespaceRepository;
        this.clock = clock;
        this.ownerId = ownerId;
        this.primaryNamespaceSlug = primaryNamespaceSlug;
        this.visibilityDepartmentExternalId = visibilityDepartmentExternalId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long primaryNamespaceId = namespaceRepository.findBySlug(primaryNamespaceSlug)
                .map(namespace -> namespace.getId())
                .orElse(null);
        Long visibilityNamespaceId = visibilityDepartmentExternalId.isBlank()
                ? primaryNamespaceId
                : namespaceRepository.findByExternalProviderAndExternalId(
                                "feishu", visibilityDepartmentExternalId)
                        .map(namespace -> namespace.getId())
                        .orElse(primaryNamespaceId);
        int imported = 0;
        for (ToolSeed seed : seeds()) {
            if (upsert(seed, primaryNamespaceId, visibilityNamespaceId)) {
                imported++;
            }
        }
        log.info("JoyHub Catalog seed ready: {} Bingo Voyage tools", imported);
    }

    private boolean upsert(ToolSeed seed, Long primaryNamespaceId, Long visibilityNamespaceId) {
        String sourceKey = SOURCE_PREFIX + seed.slug();
        CatalogResourceDraft draft = draft(seed, primaryNamespaceId, visibilityNamespaceId);
        CatalogResource resource = repository.findBySourceKey(sourceKey).orElse(null);
        if (resource == null) {
            if (repository.findBySlug(seed.slug()).isPresent()) {
                log.warn("Skip Catalog seed because employee-authored slug already exists: {}", seed.slug());
                return false;
            }
            resource = new CatalogResource(draft, ownerId);
            resource.setSourceKey(sourceKey);
        } else {
            resource.update(draft);
        }
        resource.publish(clock.instant());
        repository.save(resource);
        return true;
    }

    private CatalogResourceDraft draft(
            ToolSeed seed,
            Long primaryNamespaceId,
            Long visibilityNamespaceId
    ) {
        return new CatalogResourceDraft(
                seed.slug(),
                seed.name(),
                seed.summary(),
                CatalogResourceKind.ONLINE_TOOL,
                seed.icon(),
                seed.url(),
                documentation(seed),
                "1.0",
                null,
                null,
                null,
                null,
                Set.of(),
                primaryNamespaceId,
                CatalogMaintenanceStatus.ACTIVE,
                visibilityNamespaceId == null
                        ? CatalogVisibilityScope.COMPANY : CatalogVisibilityScope.DEPARTMENTS,
                visibilityNamespaceId == null ? Set.of() : Set.of(visibilityNamespaceId),
                seed.scenarios(),
                seed.tags(),
                Set.of(),
                Set.of()
        );
    }

    private String documentation(ToolSeed seed) {
        return """
                # %s

                %s

                ## 使用方法

                %s

                ## 使用前准备

                - 当前设备已连接公司内网或 VPN，并拥有目标系统所需权限。
                - 涉及生产环境、配置变更或资源发布时，先确认操作对象和影响范围。
                - 工具中的项目数据、日志、构建产物和访问地址仅限公司内部使用。

                ## 访问与支持

                - 入口：`%s`
                - 维护团队：Bingo Voyage 项目团队
                - 无法访问时：先确认公司网络和账号权限，再把工具名称、发生时间和页面截图发给维护团队。
                """.formatted(seed.name(), seed.summary(), seed.guide(), seed.url());
    }

    private List<ToolSeed> seeds() {
        return List.of(
                new ToolSeed(
                        "bv-team-schedule",
                        "BV 人员排期表",
                        "查看 Bingo Voyage 团队成员排期与资源安排。",
                        "👥",
                        "https://joycastle.feishu.cn/wiki/wikcnkot4X55otMpYPfitUtpMKf",
                        "打开飞书排期表后，按成员、日期或项目阶段查看安排。调整排期前先与相关负责人确认，避免覆盖他人维护的内容。",
                        Set.of("项目协作", "资源排期"),
                        Set.of("Bingo Voyage", "飞书", "排期")
                ),
                new ToolSeed(
                        "bv-config-sheet",
                        "BV 配表入口",
                        "进入 Bingo Voyage 配表工作表，查看和维护项目配置。",
                        "📋",
                        "https://docs.google.com/spreadsheets/d/1z4sKu6XQJDcsRagUFjvZWp-jXh_uvzFfkcOSN7q9AIA/edit?gid=0#gid=0",
                        "打开工作表后先确认目标分页、环境和配置版本。修改前保留原值或变更记录，完成后按项目流程进行校验和同步。",
                        Set.of("配置管理", "项目协作"),
                        Set.of("Bingo Voyage", "配表", "Google Sheets")
                ),
                new ToolSeed(
                        "bv-gm-bingo-tool",
                        "GM Bingo Tool",
                        "管理 Bingo 游戏附件与运营资源。",
                        "🧰",
                        "https://bingotool.superbgame.net/attach/",
                        "进入附件管理页面，选择目标资源并核对所属环境、版本和文件内容。上传、替换或删除资源前，按项目发布流程完成确认。",
                        Set.of("运营配置", "资源管理"),
                        Set.of("Bingo Voyage", "GM", "运营资源")
                ),
                new ToolSeed(
                        "bv-dev-gm",
                        "BV Dev GM 后台",
                        "管理 Bingo Voyage 开发环境的 GM 数据与功能。",
                        "🛠️",
                        "https://bingo2-dev-gm.superbgame.net/overview",
                        "进入后台后确认当前环境为开发环境，再选择目标功能进行调试或数据验证。记录关键参数和结果，便于复现与回溯。",
                        Set.of("开发调试", "配置管理"),
                        Set.of("Bingo Voyage", "GM", "开发环境")
                ),
                new ToolSeed(
                        "bv-prod-gm",
                        "BV Prod GM 后台",
                        "管理 Bingo Voyage 生产环境的 GM 数据与功能。",
                        "🛡️",
                        "https://bingo2-gm.superbgame.net/overview",
                        "这是生产环境入口。进入后台后先复核账号、目标玩家或配置、变更内容和影响范围；仅在获得相应授权并完成审批后执行写操作。",
                        Set.of("生产运维", "配置管理"),
                        Set.of("Bingo Voyage", "GM", "生产环境")
                ),
                new ToolSeed(
                        "bv-opensearch",
                        "BV OpenSearch 后台",
                        "查询 Bingo Voyage 日志与运行数据。",
                        "🔎",
                        "https://bingovoyage-logs.superbgame.net/",
                        "选择正确的索引和时间范围，再按请求标识、用户标识或关键字检索日志。分享排查结果前应移除敏感字段和无关用户数据。",
                        Set.of("日志查询", "问题诊断"),
                        Set.of("Bingo Voyage", "OpenSearch", "日志")
                ),
                new ToolSeed(
                        "bv-149-jenkins",
                        "BV 149 Jenkins",
                        "查看 149 环境的构建任务与流水线状态。",
                        "🏗️",
                        "http://192.168.6.149:8080/",
                        "打开 Jenkins 后选择目标任务，核对分支、参数和最近构建记录。触发或重跑任务前确认不会影响其他成员正在使用的环境。",
                        Set.of("持续集成", "构建发布"),
                        Set.of("Bingo Voyage", "Jenkins", "149 环境")
                ),
                new ToolSeed(
                        "bv-149-builds",
                        "BV 149 构建下载",
                        "下载 149 环境生成的 Bingo Voyage 构建产物。",
                        "📦",
                        "http://192.168.6.149:8000/",
                        "按平台、分支、版本号和生成时间选择构建产物。下载后保留构建标识，测试反馈中应明确使用的具体版本。",
                        Set.of("构建下载", "测试验证"),
                        Set.of("Bingo Voyage", "构建产物", "149 环境")
                ),
                new ToolSeed(
                        "bv-150-jenkins",
                        "BV 150 Jenkins",
                        "查看 150 环境的构建任务与流水线状态。",
                        "🏗️",
                        "http://192.168.6.150:8080/",
                        "打开 Jenkins 后选择目标任务，核对分支、参数和最近构建记录。触发或重跑任务前确认不会影响其他成员正在使用的环境。",
                        Set.of("持续集成", "构建发布"),
                        Set.of("Bingo Voyage", "Jenkins", "150 环境")
                ),
                new ToolSeed(
                        "bv-150-builds",
                        "BV 150 构建下载",
                        "下载 150 环境生成的 Bingo Voyage 构建产物。",
                        "📦",
                        "http://192.168.6.150:8000/",
                        "按平台、分支、版本号和生成时间选择构建产物。下载后保留构建标识，测试反馈中应明确使用的具体版本。",
                        Set.of("构建下载", "测试验证"),
                        Set.of("Bingo Voyage", "构建产物", "150 环境")
                ),
                new ToolSeed(
                        "bv-svn-resources",
                        "BV SVN 资源",
                        "浏览与获取 Bingo Voyage SVN 项目资源。",
                        "🗂️",
                        "http://192.168.6.150:8088/",
                        "按项目目录和版本定位资源，下载前核对路径、提交记录和目标版本。不要在未确认来源的情况下覆盖本地或共享资源。",
                        Set.of("资源管理", "版本管理"),
                        Set.of("Bingo Voyage", "SVN", "项目资源")
                )
        );
    }

    private record ToolSeed(
            String slug,
            String name,
            String summary,
            String icon,
            String url,
            String guide,
            Set<String> scenarios,
            Set<String> tags
    ) {
    }
}
