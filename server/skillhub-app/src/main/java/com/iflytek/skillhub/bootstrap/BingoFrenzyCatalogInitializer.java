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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently imports the first real JoyHub 2.0 online-tool catalog when explicitly enabled. */
@Component
@ConditionalOnProperty(name = "joyhub.catalog.seed.bingo-frenzy.enabled", havingValue = "true")
@Order(100)
public class BingoFrenzyCatalogInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BingoFrenzyCatalogInitializer.class);
    private static final String SOURCE_PREFIX = "bingo-frenzy-tools:";

    private final CatalogResourceRepository repository;
    private final NamespaceRepository namespaceRepository;
    private final Clock clock;
    private final String ownerId;
    private final String primaryNamespaceSlug;
    private final String visibilityDepartmentExternalId;

    public BingoFrenzyCatalogInitializer(CatalogResourceRepository repository,
                                         NamespaceRepository namespaceRepository,
                                         Clock clock,
                                         @Value("${joyhub.catalog.seed.bingo-frenzy.owner-id:local-admin}") String ownerId,
                                         @Value("${joyhub.catalog.seed.bingo-frenzy.primary-namespace-slug:gestalt}") String primaryNamespaceSlug,
                                         @Value("${joyhub.catalog.seed.bingo-frenzy.visibility-department-external-id:}") String visibilityDepartmentExternalId) {
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
                : namespaceRepository.findByExternalProviderAndExternalId("feishu", visibilityDepartmentExternalId)
                        .map(namespace -> namespace.getId())
                        .orElse(primaryNamespaceId);
        int imported = 0;
        for (ToolSeed seed : seeds()) {
            if (upsert(seed, primaryNamespaceId, visibilityNamespaceId)) {
                imported++;
            }
        }
        log.info("JoyHub Catalog seed ready: {} Bingo Frenzy tools", imported);
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

    private CatalogResourceDraft draft(ToolSeed seed, Long primaryNamespaceId, Long visibilityNamespaceId) {
        String documentation = """
                # %s

                %s

                ## 快速开始

                1. 确认当前设备已连接公司内网或 VPN。
                2. 点击页面上的“打开工具”。
                3. 如页面无法访问，请联系格式塔工作室 Bingo 项目维护团队检查服务状态和访问权限。

                ## 访问地址

                `%s`

                ## 维护信息

                - 来源：Bingo Frenzy 开发工具集
                - 网络范围：公司内网
                - 首批接入方式：JoyHub 2.0 Catalog 自动导入
                """.formatted(seed.name(), seed.summary(), seed.url());
        return new CatalogResourceDraft(
                seed.slug(),
                seed.name(),
                seed.summary(),
                CatalogResourceKind.ONLINE_TOOL,
                seed.icon(),
                seed.url(),
                documentation,
                "1.0",
                null,
                null,
                null,
                null,
                Set.of(),
                primaryNamespaceId,
                CatalogMaintenanceStatus.ACTIVE,
                visibilityNamespaceId == null ? CatalogVisibilityScope.COMPANY : CatalogVisibilityScope.DEPARTMENTS,
                visibilityNamespaceId == null ? Set.of() : Set.of(visibilityNamespaceId),
                seed.scenarios(),
                seed.tags(),
                Set.of(),
                Set.of()
        );
    }

    private List<ToolSeed> seeds() {
        return List.of(
                new ToolSeed("bingo-web-game", "Web 版游戏", "浏览器版本的 Bingo Frenzy 游戏，用于测试。",
                        "🌐", "https://192.168.6.105:8001/", Set.of("游戏研发", "测试验证"), Set.of("Bingo Frenzy", "Web")),
                new ToolSeed("bingo-build-packages", "构建包", "Bingo Frenzy 游戏构建包下载入口。",
                        "📦", "https://192.168.6.105:8002/", Set.of("研发提效", "构建发布"), Set.of("Bingo Frenzy", "构建")),
                new ToolSeed("spine-preview", "Spine 预览", "Spine 动画资源预览和上传工具。",
                        "🦴", "https://192.168.6.105:9001/", Set.of("美术资产处理", "动画预览"), Set.of("Spine", "美术")),
                new ToolSeed("fmod-preview", "FMOD 预览", "音频资源预览和上传工具。",
                        "🔊", "https://192.168.6.105:8004/", Set.of("音频制作", "资源预览"), Set.of("FMOD", "音频")),
                new ToolSeed("curve-editor", "曲线编辑器", "横版大地图路线编辑工具。",
                        "📈", "https://192.168.6.105/curve-editor/", Set.of("游戏研发", "关卡编辑"), Set.of("曲线", "编辑器")),
                new ToolSeed("bingo-card-simulator", "玩法模拟器", "游戏玩法逻辑测试和玩法动画曲线编辑工具。",
                        "🎮", "https://192.168.6.105/bingo-frenzy-card-simulator/", Set.of("游戏研发", "玩法验证"), Set.of("模拟器", "测试")),
                new ToolSeed("slice9-editor", "九宫切图", "UI 图片九宫格切图工具。",
                        "🔪", "https://192.168.6.105/slice9-editor/", Set.of("美术资产处理", "UI 制作"), Set.of("九宫格", "图片")),
                new ToolSeed("atlas-unpacker", "图集拆分", "图集拆分和资源提取工具。",
                        "✂️", "https://192.168.6.105/atlas-unpacker/", Set.of("美术资产处理", "资源提取"), Set.of("图集", "图片"))
        );
    }

    private record ToolSeed(
            String slug,
            String name,
            String summary,
            String icon,
            String url,
            Set<String> scenarios,
            Set<String> tags
    ) {
    }
}
