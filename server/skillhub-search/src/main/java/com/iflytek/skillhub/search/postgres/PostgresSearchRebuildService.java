package com.iflytek.skillhub.search.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import com.iflytek.skillhub.search.SkillSearchDocument;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reconstructs PostgreSQL search documents from canonical skill and namespace records.
 */
@Service
public class PostgresSearchRebuildService implements SearchRebuildService {
    private static final Set<String> RESERVED_FRONTMATTER_FIELDS = Set.of("name", "description", "version");
    private static final Set<String> KEYWORD_FIELD_NAMES = Set.of("keywords", "keyword", "tags", "tag");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_INDEXED_FILE_BYTES = 256 * 1024;
    private static final int MAX_INDEXED_DOCUMENT_BYTES = 1024 * 1024;
    private static final String PRIMARY_SKILL_DOCUMENT = "skill.md";

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final LabelTranslationRepository labelTranslationRepository;
    private final SkillLabelRepository skillLabelRepository;
    private final SearchIndexService searchIndexService;
    private final SearchTextTokenizer searchTextTokenizer;
    private final ObjectMapper objectMapper;

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SearchIndexService searchIndexService,
            SearchTextTokenizer searchTextTokenizer) {
        this(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                null,
                null,
                null,
                null,
                null,
                searchIndexService,
                searchTextTokenizer
        );
    }

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            LabelDefinitionRepository labelDefinitionRepository,
            LabelTranslationRepository labelTranslationRepository,
            SkillLabelRepository skillLabelRepository,
            SearchIndexService searchIndexService,
            SearchTextTokenizer searchTextTokenizer) {
        this(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                null,
                null,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );
    }

    @Autowired
    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            ObjectStorageService objectStorageService,
            LabelDefinitionRepository labelDefinitionRepository,
            LabelTranslationRepository labelTranslationRepository,
            SkillLabelRepository skillLabelRepository,
            SearchIndexService searchIndexService,
            SearchTextTokenizer searchTextTokenizer) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.labelTranslationRepository = labelTranslationRepository;
        this.skillLabelRepository = skillLabelRepository;
        this.searchIndexService = searchIndexService;
        this.searchTextTokenizer = searchTextTokenizer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void rebuildAll() {
        List<SkillSearchDocument> documents = skillRepository.findAll().stream()
                .filter(skill -> skill.getStatus() == SkillStatus.ACTIVE)
                .map(this::toDocument)
                .flatMap(Optional::stream)
                .toList();
        searchIndexService.batchIndex(documents);
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        List<Skill> skills = skillRepository.findByNamespaceIdAndStatus(namespaceId, SkillStatus.ACTIVE);

        for (Skill skill : skills) {
            rebuildBySkill(skill.getId());
        }
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        Optional<Skill> skillOpt = skillRepository.findById(skillId);
        if (skillOpt.isEmpty()) {
            return;
        }

        toDocument(skillOpt.get()).ifPresent(searchIndexService::index);
    }

    private SearchIndexPayload buildSearchPayload(Skill skill) {
        List<String> searchParts = new ArrayList<>();
        addPart(searchParts, skill.getSlug());
        addPart(searchParts, skill.getLocalizedDisplayName());
        addPart(searchParts, skill.getSummary());
        addPart(searchParts, skill.getLocalizedSummary());

        Set<String> keywords = new TreeSet<>();
        Optional<SkillVersion> latestVersion = resolveLatestVersion(skill);
        latestVersion
                .map(this::extractParsedMetadata)
                .map(metadata -> metadata.get("frontmatter"))
                .map(this::asMap)
                .ifPresent(frontmatter -> appendFrontmatter(frontmatter, keywords, searchParts));
        latestVersion.ifPresent(version -> appendDocumentContents(version, searchParts));
        appendLabelKeywords(skill.getId(), keywords);

        return new SearchIndexPayload(
                searchTextTokenizer.enrichForIndex(String.join(" ", keywords)),
                searchTextTokenizer.enrichForIndex(String.join(" ", searchParts).trim())
        );
    }

    private void appendDocumentContents(SkillVersion version, List<String> searchParts) {
        if (skillFileRepository == null || objectStorageService == null) {
            return;
        }
        int indexedBytes = 0;
        List<SkillFile> files = skillFileRepository.findByVersionId(version.getId()).stream()
                .filter(this::isSearchableTextFile)
                .sorted(java.util.Comparator.comparing(SkillFile::getFilePath))
                .toList();
        for (SkillFile file : files) {
            if (indexedBytes >= MAX_INDEXED_DOCUMENT_BYTES) {
                break;
            }
            int limit = Math.min(MAX_INDEXED_FILE_BYTES, MAX_INDEXED_DOCUMENT_BYTES - indexedBytes);
            try (InputStream input = objectStorageService.getObject(file.getStorageKey())) {
                byte[] content = input.readNBytes(limit + 1);
                int accepted = Math.min(content.length, limit);
                addPart(searchParts, file.getFilePath());
                addPart(searchParts, new String(content, 0, accepted, StandardCharsets.UTF_8));
                indexedBytes += accepted;
            } catch (IOException | RuntimeException ignored) {
                // A missing auxiliary document must not make the canonical search rebuild fail.
            }
        }
    }

    private boolean isSearchableTextFile(SkillFile file) {
        String path = file.getFilePath().replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        return file.getFileSize() <= MAX_INDEXED_FILE_BYTES
                && PRIMARY_SKILL_DOCUMENT.equals(path.toLowerCase(Locale.ROOT));
    }

    private Optional<SkillVersion> resolveLatestVersion(Skill skill) {
        if (skill.getLatestVersionId() == null) {
            return Optional.empty();
        }
        return skillVersionRepository.findById(skill.getLatestVersionId());
    }

    private Map<String, Object> extractParsedMetadata(SkillVersion version) {
        String metadataJson = version.getParsedMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return normalized;
        }
        return Map.of();
    }

    private void appendFrontmatter(Map<String, Object> frontmatter, Set<String> keywords, List<String> searchParts) {
        for (Map.Entry<String, Object> entry : frontmatter.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);

            if (KEYWORD_FIELD_NAMES.contains(normalizedFieldName)) {
                flattenToStrings(value).forEach(keyword -> {
                    String normalized = keyword.trim();
                    if (!normalized.isBlank()) {
                        keywords.add(normalized);
                    }
                });
            }

            if (!RESERVED_FRONTMATTER_FIELDS.contains(normalizedFieldName)
                    && !KEYWORD_FIELD_NAMES.contains(normalizedFieldName)) {
                addPart(searchParts, fieldName);
                flattenToStrings(value).forEach(text -> addPart(searchParts, text));
            }
        }
    }

    private List<String> flattenToStrings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return List.of(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    values.add(String.valueOf(entry.getKey()));
                }
                if (entry.getValue() != null) {
                    values.addAll(flattenToStrings(entry.getValue()));
                }
            }
            return values;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .flatMap(item -> flattenToStrings(item).stream())
                    .toList();
        }
        return List.of(String.valueOf(value));
    }

    private void addPart(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private void appendLabelKeywords(Long skillId, Set<String> keywords) {
        if (skillLabelRepository == null || labelDefinitionRepository == null || labelTranslationRepository == null) {
            return;
        }
        List<Long> labelIds = skillLabelRepository.findBySkillId(skillId).stream()
                .map(skillLabel -> skillLabel.getLabelId())
                .distinct()
                .toList();
        if (labelIds.isEmpty()) {
            return;
        }
        Map<Long, LabelDefinition> definitionsById = labelDefinitionRepository.findByIdIn(labelIds).stream()
                .collect(java.util.stream.Collectors.toMap(LabelDefinition::getId, definition -> definition));
        for (LabelTranslation translation : labelTranslationRepository.findByLabelIdIn(labelIds)) {
            if (definitionsById.containsKey(translation.getLabelId())) {
                addKeyword(keywords, translation.getDisplayName());
            }
        }
    }

    private void addKeyword(Set<String> keywords, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            keywords.add(normalized);
        }
    }

    private Optional<SkillSearchDocument> toDocument(Skill skill) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findById(skill.getNamespaceId());
        if (namespaceOpt.isEmpty()) {
            return Optional.empty();
        }

        Namespace namespace = namespaceOpt.get();
        SearchIndexPayload payload = buildSearchPayload(skill);

        return Optional.of(new SkillSearchDocument(
                skill.getId(),
                skill.getNamespaceId(),
                namespace.getSlug(),
                skill.getOwnerId(),
                skill.getDisplayName() != null ? skill.getDisplayName() : skill.getSlug(),
                skill.getSummary(),
                payload.keywords(),
                payload.searchText(),
                null,
                skill.getVisibility().name(),
                skill.getStatus().name()
        ));
    }

    private record SearchIndexPayload(String keywords, String searchText) {
    }
}
