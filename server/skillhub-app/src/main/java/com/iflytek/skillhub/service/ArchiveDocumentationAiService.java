package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ArchiveDocumentationDraftResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

/** Safely turns an uploaded Tool ZIP into bounded textual evidence for AI documentation. */
@Service
public class ArchiveDocumentationAiService {
    private static final int MAX_FILE_CHARS = 6_000;
    private static final int MAX_EVIDENCE_CHARS = 36_000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml", ".toml", ".xml", ".ini", ".cfg", ".env",
            ".js", ".cjs", ".mjs", ".ts", ".tsx", ".jsx", ".py", ".sh", ".bash", ".zsh", ".java",
            ".go", ".rs", ".rb", ".php", ".sql", ".html", ".css", ".csv", ".properties"
    );

    private final DiscoveryAiProperties properties;
    private final SkillPackageArchiveExtractor archiveExtractor;
    private final OpenAiResponsesClient aiClient;

    public ArchiveDocumentationAiService(DiscoveryAiProperties properties,
                                         SkillPackageArchiveExtractor archiveExtractor,
                                         OpenAiResponsesClient aiClient) {
        this.properties = properties;
        this.archiveExtractor = archiveExtractor;
        this.aiClient = aiClient;
    }

    public ArchiveDocumentationDraftResponse draft(MultipartFile file, String userId, String language) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw CatalogDomainException.badRequest("error.catalog.ai.unavailable");
        }
        if (!isZip(file.getOriginalFilename())) {
            throw CatalogDomainException.badRequest("error.catalog.archive.readFailed");
        }
        try {
            String evidence = evidence(archiveExtractor.extract(file));
            if (evidence.isBlank()) {
                throw CatalogDomainException.badRequest("error.catalog.archive.noText");
            }
            return aiClient.generateArchiveDocumentation(evidence, language, safetyIdentifier(userId));
        } catch (IOException | IllegalArgumentException exception) {
            throw CatalogDomainException.badRequest("error.catalog.archive.readFailed");
        } catch (RuntimeException exception) {
            if (exception instanceof CatalogDomainException) {
                throw exception;
            }
            throw CatalogDomainException.badRequest("error.catalog.ai.failed");
        }
    }

    private String evidence(List<PackageEntry> entries) {
        StringBuilder result = new StringBuilder("Archive file list:\n");
        entries.forEach(entry -> result.append("- ").append(entry.path()).append('\n'));
        result.append("\nRelevant text files:\n");
        entries.stream()
                .filter(entry -> isTextFile(entry.path()))
                .sorted(Comparator.comparingInt((PackageEntry entry) -> priority(entry.path())).reversed()
                        .thenComparing(PackageEntry::path))
                .forEach(entry -> appendEntry(result, entry));
        return result.toString();
    }

    private void appendEntry(StringBuilder result, PackageEntry entry) {
        if (result.length() >= MAX_EVIDENCE_CHARS) return;
        String content = new String(entry.content(), StandardCharsets.UTF_8)
                .replace("\u0000", "")
                .trim();
        if (content.isBlank()) return;
        int remaining = MAX_EVIDENCE_CHARS - result.length();
        int limit = Math.min(MAX_FILE_CHARS, Math.max(0, remaining - entry.path().length() - 32));
        if (limit <= 0) return;
        if (content.length() > limit) content = content.substring(0, limit) + "\n[truncated]";
        result.append("\n--- ").append(entry.path()).append(" ---\n").append(content).append('\n');
    }

    private boolean isTextFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith)
                || lower.endsWith("dockerfile") || lower.endsWith("makefile") || lower.endsWith("readme");
    }

    private int priority(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("skill.md")) return 100;
        if (lower.contains("readme")) return 90;
        if (lower.endsWith("package.json") || lower.endsWith("pyproject.toml") || lower.endsWith("dockerfile")) return 80;
        if (lower.endsWith("docker-compose.yml") || lower.endsWith("docker-compose.yaml")
                || lower.endsWith("requirements.txt")) return 70;
        return 10;
    }

    private String safetyIdentifier(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            return "joyhub-archive-doc-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isZip(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".zip");
    }
}
