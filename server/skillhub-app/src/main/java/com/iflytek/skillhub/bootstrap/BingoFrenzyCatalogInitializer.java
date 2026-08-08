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
                visibilityNamespaceId == null ? CatalogVisibilityScope.COMPANY : CatalogVisibilityScope.DEPARTMENTS,
                visibilityNamespaceId == null ? Set.of() : Set.of(visibilityNamespaceId),
                seed.scenarios(),
                seed.tags(),
                Set.of(),
                Set.of()
        );
    }

    private String documentation(ToolSeed seed) {
        String guide = switch (seed.slug()) {
            case "atlas-unpacker" -> """
                    ## 适合什么情况

                    当美术或开发拿到一张合并图集，需要把其中的单张图片重新提取出来时使用。适合临时查看、资源核对和批量拆分，不需要安装桌面软件。

                    ## 操作步骤

                    1. 打开工具，按页面提示选择图集图片；如果工具要求配套的图集描述文件，请一并选择。
                    2. 等待工具识别图集中的图片区域，先检查数量、名称和边界是否合理。
                    3. 选择需要的图片，执行拆分或导出。
                    4. 下载后抽查透明边缘、尺寸和命名，确认没有错位或被裁切。

                    ## 常见问题

                    - **没有识别到图片**：检查图集图片与描述文件是否属于同一版本，并按页面支持的格式重新选择。
                    - **拆出的图片错位**：通常是图集与描述文件不匹配，或资源在生成图集后又被替换。
                    - **透明边缘异常**：先核对原图集是否带预乘透明或特殊压缩，再联系维护团队确认处理方式。
                    """;
            case "slice9-editor" -> """
                    ## 适合什么情况

                    用于制作可拉伸的 UI 背景，例如按钮、面板、弹窗和气泡。通过设置四条切分线，让四角保持不变、边缘单向拉伸、中心区域自由拉伸。

                    ## 操作步骤

                    1. 上传需要处理的 UI 图片。
                    2. 拖动九宫格切分线，把圆角、描边和装饰元素留在不会被拉伸的区域。
                    3. 使用页面预览检查横向、纵向和同时拉伸的效果。
                    4. 确认四角不变形、边缘不断裂后，再按页面提示导出结果或记录参数。

                    ## 检查要点

                    - 四个角不应进入拉伸区域。
                    - 描边和渐变尽量保持连续，中心区域应允许重复或拉伸。
                    - 如果预览出现模糊、断线或圆角变形，重新调整切分线，不要直接交付资源。
                    """;
            case "bingo-card-simulator" -> """
                    ## 适合什么情况

                    在进入完整游戏构建前，快速验证 Bingo 玩法逻辑、卡牌表现和动画曲线。适合策划、客户端和动画同学一起对齐规则与手感。

                    ## 操作步骤

                    1. 打开模拟器，按当前验证目标设置玩法或卡牌参数。
                    2. 启动一次完整流程，观察触发顺序、状态变化和动画节奏。
                    3. 需要调整表现时，在曲线编辑区域修改关键点，再重新播放对比。
                    4. 每次只改少量参数，并记录可复现的配置、预期结果和实际结果。

                    ## 提交问题时请提供

                    - 使用的配置或页面截图。
                    - 从哪一步开始与预期不一致。
                    - 浏览器、发生时间，以及是否可以稳定复现。
                    """;
            case "curve-editor" -> """
                    ## 适合什么情况

                    用于编辑横版大地图中的路线曲线，快速调整节点位置、路径走向和整体节奏，并在交付前检查路线是否自然、连续。

                    ## 操作步骤

                    1. 打开工具并载入现有路线，或新建一条路线。
                    2. 添加、选择并拖动节点，调整各段曲线的方向和弯曲程度。
                    3. 从起点到终点完整检查一次，避免急弯、回折、断点或节点重叠。
                    4. 按页面提供的方式保存或导出，并在目标版本中再次验证。

                    ## 使用建议

                    - 先确定关键位置，再补中间节点，避免一开始堆太多控制点。
                    - 小步调整并频繁预览；如果曲线局部抖动，优先减少或重新排列附近节点。
                    """;
            case "fmod-preview" -> """
                    ## 适合什么情况

                    用于在浏览器中快速上传和试听音频资源，核对内容、音量、循环与版本是否正确，减少仅为试听而反复安装或构建客户端的成本。

                    ## 操作步骤

                    1. 按页面提示选择受支持的 FMOD 或音频资源。
                    2. 等待资源加载完成，再选择需要试听的事件或音频条目。
                    3. 播放并检查开头、结尾、循环衔接、响度以及左右声道。
                    4. 上传或交付前，确认资源名称和目标版本一致。

                    ## 常见问题

                    - **无法播放**：检查资源是否完整、格式是否受支持，并尝试重新加载页面。
                    - **缺少事件或声音**：确认选择的是同一批次的配套文件。
                    - **声音与客户端不同**：记录资源版本、事件名称和差异表现，交给音频或客户端同学复核运行参数。
                    """;
            case "spine-preview" -> """
                    ## 适合什么情况

                    用于快速预览 Spine 动画资源，检查动画、皮肤、贴图和版本是否配套。适合美术自检，也适合策划和开发在接入前确认表现。

                    ## 操作步骤

                    1. 按页面提示选择完整的一组 Spine 资源；常见组合包括骨骼数据、atlas 和贴图。
                    2. 资源加载后，选择动画和皮肤，分别播放常用状态。
                    3. 检查贴图缺失、部件错位、动作跳变、循环衔接和画面裁切。
                    4. 确认资源名称、导出版本和目标项目要求一致后再交付。

                    ## 常见问题

                    - **白块或贴图缺失**：通常是 atlas、贴图或骨骼数据不配套。
                    - **动画列表为空**：检查是否选择了正确的骨骼数据，以及导出版本是否受支持。
                    - **显示比例异常**：先核对导出缩放和项目约定，不要只在预览页面中目测修正。
                    """;
            case "bingo-build-packages" -> """
                    ## 适合什么情况

                    用于查找和下载 Bingo Frenzy 的测试构建包。适合测试、策划和研发获取指定平台、分支或时间点的版本。

                    ## 操作步骤

                    1. 打开工具，先确认要验证的平台、版本或构建时间。
                    2. 在列表中找到目标构建，核对名称、分支、版本号和生成时间。
                    3. 下载构建包，并保留对应的版本信息用于问题反馈。
                    4. 安装或解压后先完成基础启动检查，再进入具体测试场景。

                    ## 注意事项

                    - 不要仅凭文件名猜测版本；找不到目标包时先向发布人确认构建是否成功。
                    - 反馈问题时附上构建标识、平台、发生时间和复现步骤，避免混用不同版本的结论。
                    """;
            case "bingo-web-game" -> """
                    ## 适合什么情况

                    无需安装客户端即可在浏览器中体验 Bingo Frenzy 测试版本，适合快速验证玩法、页面流程和基础兼容性。

                    ## 操作步骤

                    1. 打开工具，等待游戏资源加载完成。
                    2. 确认当前测试目标和版本，再按正常玩家路径完成一遍操作。
                    3. 发现问题时记录浏览器、页面版本、操作步骤、预期结果和实际结果。
                    4. 刷新或重新开始后再次验证，判断问题是否可以稳定复现。

                    ## 注意事项

                    - 这是测试入口，不要把测试进度或数据当作正式环境数据。
                    - 首次加载可能较慢；若长时间停在加载页，先检查公司网络，再截图反馈给维护团队。
                    """;
            default -> """
                    ## 使用方法

                    打开工具后按页面提示完成操作。交付结果前，请记录使用的输入、版本和验证结论。
                    """;
        };
        return """
                # %s

                %s

                %s

                ## 使用前准备

                - 当前设备已连接公司内网或 VPN。
                - 准备好要处理的资源，并先保留原文件备份。
                - 不确定输入格式时，以工具页面提示为准。

                ## 访问与支持

                - 入口：`%s`
                - 维护团队：格式塔工作室 Bingo 项目团队
                - 无法访问时：先确认公司网络，再把工具名称、发生时间和页面截图发给维护团队。
                """.formatted(seed.name(), seed.summary(), guide, seed.url());
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
