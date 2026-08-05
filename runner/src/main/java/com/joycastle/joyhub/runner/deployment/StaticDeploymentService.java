package com.joycastle.joyhub.runner.deployment;

import com.joycastle.joyhub.runner.api.RunnerDeploymentRequest;
import com.joycastle.joyhub.runner.api.RunnerDeploymentResult;
import com.joycastle.joyhub.runner.api.RunnerOfflineRequest;
import com.joycastle.joyhub.runner.api.RunnerStateResponse;
import com.joycastle.joyhub.runner.api.RunnerSwitchRequest;
import com.joycastle.joyhub.runner.config.RunnerProperties;
import com.joycastle.joyhub.runner.exception.RunnerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.stereotype.Service;

@Service
public class StaticDeploymentService {
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "html", "htm", "css", "js", "mjs", "map", "json", "txt", "xml", "svg",
            "png", "jpg", "jpeg", "gif", "webp", "ico", "woff", "woff2", "ttf", "otf",
            "wasm", "webmanifest", "pdf", "mp3", "mp4", "webm"
    );

    private final RunnerProperties properties;
    private final StaticDeploymentVerifier verifier;

    public StaticDeploymentService(RunnerProperties properties, StaticDeploymentVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
    }

    public synchronized RunnerDeploymentResult deploy(RunnerDeploymentRequest request, byte[] zipBytes) {
        validateDeploymentRequest(request, zipBytes);
        Path target = releasePath(request.slug(), request.releaseId());
        String expectedSha = request.artifactSha256().toLowerCase(Locale.ROOT);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            String storedSha = readStoredSha(request.slug(), request.releaseId());
            if (!expectedSha.equals(storedSha)) {
                throw new RunnerException("IMMUTABLE_RELEASE_CONFLICT",
                        "Release directory already exists with different content");
            }
            switchAndVerify(request.slug(), request.releaseId(), request.jobId());
            return success("Release was already prepared and is active", request.slug(), request.releaseId());
        }

        Path candidate = workPath(request.jobId());
        try {
            Files.createDirectories(candidate);
            extract(zipBytes, candidate);
            requireIndex(candidate);
            writeStoredSha(candidate, expectedSha);
            Files.createDirectories(target.getParent());
            movePreparedDirectory(candidate, target);
            switchAndVerify(request.slug(), request.releaseId(), request.jobId());
            return success("Static release deployed", request.slug(), request.releaseId());
        } catch (RunnerException exception) {
            deleteRecursively(candidate);
            throw exception;
        } catch (IOException exception) {
            deleteRecursively(candidate);
            throw new RunnerException("STATIC_DEPLOYMENT_IO_FAILED", "Static deployment filesystem operation failed", exception);
        }
    }

    public synchronized RunnerDeploymentResult rollback(RunnerSwitchRequest request) {
        validateSwitchRequest(request);
        requireRetainedRelease(request.slug(), request.releaseId());
        switchAndVerify(request.slug(), request.releaseId(), request.jobId());
        return success("Static release rolled back", request.slug(), request.releaseId());
    }

    public synchronized RunnerDeploymentResult restore(RunnerSwitchRequest request) {
        validateSwitchRequest(request);
        requireRetainedRelease(request.slug(), request.releaseId());
        switchAndVerify(request.slug(), request.releaseId(), request.jobId());
        return success("Static release restored", request.slug(), request.releaseId());
    }

    public synchronized RunnerDeploymentResult offline(RunnerOfflineRequest request) {
        requireSlug(request.slug());
        Path live = publishedPath(request.slug());
        try {
            Files.createDirectories(live.getParent());
            if (!Files.exists(live, LinkOption.NOFOLLOW_LINKS)) {
                return RunnerDeploymentResult.success("Application is already offline", null,
                        properties.verificationUrl(request.slug()));
            }
            if (!Files.isSymbolicLink(live)) {
                throw new RunnerException("PUBLISHED_PATH_INVALID", "Published application path is not a symbolic link");
            }
            Path retired = live.resolveSibling("." + request.slug() + ".offline-" + request.jobId());
            Files.deleteIfExists(retired);
            atomicMove(live, retired, false);
            try {
                Files.deleteIfExists(retired);
            } catch (IOException ignored) {
                // The public path has already been removed atomically. A hidden stale
                // symlink is safe and can be replaced by a later offline operation.
            }
            return RunnerDeploymentResult.success("Static application taken offline", null,
                    properties.verificationUrl(request.slug()));
        } catch (IOException exception) {
            throw new RunnerException("STATIC_OFFLINE_FAILED", "Unable to take static application offline", exception);
        }
    }

    public RunnerStateResponse state(String slug) {
        requireSlug(slug);
        Path live = publishedPath(slug);
        if (!Files.isSymbolicLink(live)) {
            return new RunnerStateResponse(slug, false, null);
        }
        try {
            Path target = Files.readSymbolicLink(live);
            Path filename = target.getFileName();
            return new RunnerStateResponse(slug, true, filename != null ? filename.toString() : null);
        } catch (IOException exception) {
            throw new RunnerException("STATIC_STATE_READ_FAILED", "Unable to read current static release", exception);
        }
    }

    private void validateDeploymentRequest(RunnerDeploymentRequest request, byte[] zipBytes) {
        if (request == null || request.jobId() == null || request.applicationId() == null || request.releaseId() == null) {
            throw new RunnerException("INVALID_DEPLOYMENT_REQUEST", "Deployment identifiers are required");
        }
        requireSlug(request.slug());
        if (zipBytes == null || zipBytes.length == 0) {
            throw new RunnerException("ARTIFACT_REQUIRED", "Static ZIP is required");
        }
        if (zipBytes.length > properties.getMaxZipSize()) {
            throw new RunnerException("ZIP_TOO_LARGE", "Static ZIP exceeds the configured size limit");
        }
        String sha = request.artifactSha256();
        if (sha == null || !sha.matches("^[a-fA-F0-9]{64}$") || !sha256(zipBytes).equalsIgnoreCase(sha)) {
            throw new RunnerException("ARTIFACT_SHA256_MISMATCH", "Static ZIP SHA-256 does not match the request");
        }
    }

    private void validateSwitchRequest(RunnerSwitchRequest request) {
        if (request == null || request.jobId() == null || request.applicationId() == null || request.releaseId() == null) {
            throw new RunnerException("INVALID_SWITCH_REQUEST", "Switch identifiers are required");
        }
        requireSlug(request.slug());
    }

    private void requireSlug(String slug) {
        if (slug == null || slug.length() < 3 || slug.length() > 50 || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new RunnerException("INVALID_SLUG", "Application slug is invalid");
        }
    }

    private void extract(byte[] zipBytes, Path candidate) throws IOException {
        preScan(zipBytes, candidate);
        long totalBytes = 0;
        int fileCount = 0;
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipArchiveEntry entry;
            while ((entry = input.getNextZipEntry()) != null) {
                validateEntry(entry, candidate);
                Path destination = candidate.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                fileCount++;
                if (fileCount > properties.getMaxFileCount()) {
                    throw new RunnerException("ZIP_FILE_COUNT_EXCEEDED", "Static ZIP contains too many files");
                }
                Files.createDirectories(destination.getParent());
                long entryBytes = 0;
                try (OutputStream output = Files.newOutputStream(destination,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (entryBytes > properties.getMaxSingleFileSize()) {
                            throw new RunnerException("ZIP_FILE_TOO_LARGE", "Static ZIP contains an oversized file");
                        }
                        if (totalBytes > properties.getMaxExpandedSize()) {
                            throw new RunnerException("ZIP_EXPANDED_SIZE_EXCEEDED", "Static ZIP expands beyond the configured limit");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > 0 && entryBytes / compressedSize > properties.getMaxCompressionRatio()) {
                    throw new RunnerException("ZIP_COMPRESSION_RATIO_EXCEEDED", "Static ZIP contains a suspicious compression ratio");
                }
            }
        }
    }

    private void preScan(byte[] zipBytes, Path candidate) throws IOException {
        int fileCount = 0;
        long declaredExpandedSize = 0;
        Set<Path> destinations = new HashSet<>();
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(zipBytes);
             ZipFile zip = new ZipFile(channel)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntriesInPhysicalOrder();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                validateEntry(entry, candidate);
                if (!zip.canReadEntryData(entry)) {
                    throw new RunnerException("ZIP_ENTRY_UNREADABLE", "Static ZIP contains an unsupported or encrypted entry");
                }
                Path destination = candidate.resolve(entry.getName()).normalize();
                if (!destinations.add(destination)) {
                    throw new RunnerException("ZIP_DUPLICATE_ENTRY", "Static ZIP contains duplicate paths");
                }
                if (!entry.isDirectory()) {
                    fileCount++;
                    declaredExpandedSize += Math.max(entry.getSize(), 0);
                    if (fileCount > properties.getMaxFileCount()) {
                        throw new RunnerException("ZIP_FILE_COUNT_EXCEEDED", "Static ZIP contains too many files");
                    }
                    if (declaredExpandedSize > properties.getMaxExpandedSize()) {
                        throw new RunnerException("ZIP_EXPANDED_SIZE_EXCEEDED", "Static ZIP expands beyond the configured limit");
                    }
                }
            }
        }
    }

    private void validateEntry(ZipArchiveEntry entry, Path candidate) {
        String name = entry.getName();
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.contains("\\")
                || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new RunnerException("ZIP_ENTRY_INVALID", "Static ZIP contains an invalid path");
        }
        Path destination = candidate.resolve(name).normalize();
        if (!destination.startsWith(candidate)) {
            throw new RunnerException("ZIP_PATH_TRAVERSAL", "Static ZIP contains a path traversal entry");
        }
        int externalMode = (int) ((entry.getExternalAttributes() >> 16) & 0xffff);
        int unixMode = entry.getUnixMode() != 0 ? entry.getUnixMode() : externalMode;
        int type = unixMode & UnixStat.FILE_TYPE_FLAG;
        if (entry.isUnixSymlink() || type == UnixStat.LINK_FLAG) {
            throw new RunnerException("ZIP_SYMLINK_REJECTED", "Static ZIP cannot contain symbolic links");
        }
        if (type != 0 && type != UnixStat.FILE_FLAG && type != UnixStat.DIR_FLAG) {
            throw new RunnerException("ZIP_SPECIAL_FILE_REJECTED", "Static ZIP cannot contain special files");
        }
        if (!entry.isDirectory()) {
            validateExtension(name);
            if (entry.getSize() > properties.getMaxSingleFileSize()) {
                throw new RunnerException("ZIP_FILE_TOO_LARGE", "Static ZIP contains an oversized file");
            }
            long compressedSize = entry.getCompressedSize();
            if (entry.getSize() > 0 && compressedSize > 0
                    && entry.getSize() / compressedSize > properties.getMaxCompressionRatio()) {
                throw new RunnerException("ZIP_COMPRESSION_RATIO_EXCEEDED", "Static ZIP contains a suspicious compression ratio");
            }
        }
    }

    private void validateExtension(String name) {
        String filename = Path.of(name).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RunnerException("ZIP_FILE_TYPE_REJECTED", "Static ZIP contains a disallowed file type");
        }
    }

    private void requireIndex(Path candidate) {
        Path index = candidate.resolve("index.html");
        if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("INDEX_HTML_REQUIRED", "Static ZIP root must contain index.html");
        }
    }

    private void requireRetainedRelease(String slug, Long releaseId) {
        Path release = releasePath(slug, releaseId);
        if (!Files.isDirectory(release, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(release.resolve("index.html"), LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("RELEASE_NOT_RETAINED", "Requested static release is not retained");
        }
    }

    private void switchAndVerify(String slug, Long releaseId, Long jobId) {
        Path live = publishedPath(slug);
        Path desired = Path.of("..", "releases", slug, releaseId.toString());
        Path previous = null;
        try {
            Files.createDirectories(live.getParent());
            if (Files.exists(live, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isSymbolicLink(live)) {
                    throw new RunnerException("PUBLISHED_PATH_INVALID", "Published application path is not a symbolic link");
                }
                previous = Files.readSymbolicLink(live);
            }
            replaceSymlink(live, desired, jobId);
            try {
                verifier.verifyAvailable(slug);
            } catch (RuntimeException verificationFailure) {
                if (previous != null) {
                    replaceSymlink(live, previous, jobId);
                } else {
                    Files.deleteIfExists(live);
                }
                throw verificationFailure;
            }
        } catch (RunnerException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RunnerException("STATIC_SWITCH_FAILED", "Unable to switch the current static release", exception);
        }
    }

    private void replaceSymlink(Path live, Path target, Long jobId) throws IOException {
        Path temporary = live.resolveSibling("." + live.getFileName() + "." + jobId + ".tmp");
        Files.deleteIfExists(temporary);
        Files.createSymbolicLink(temporary, target);
        atomicMove(temporary, live, true);
    }

    private void movePreparedDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void atomicMove(Path source, Path target, boolean replace) throws IOException {
        if (replace) {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private RunnerDeploymentResult success(String summary, String slug, Long releaseId) {
        return RunnerDeploymentResult.success(summary, releaseId.toString(), properties.verificationUrl(slug));
    }

    private Path releasePath(String slug, Long releaseId) {
        return basePath().resolve("releases").resolve(slug).resolve(releaseId.toString());
    }

    private Path publishedPath(String slug) {
        return basePath().resolve("published").resolve(slug);
    }

    private Path workPath(Long jobId) {
        return basePath().resolve("work").resolve(jobId + "-" + UUID.randomUUID());
    }

    private Path shaPath(String slug, Long releaseId) {
        return releasePath(slug, releaseId).resolve(".joyhub-release.sha256");
    }

    private Path basePath() {
        return properties.getDataPath().toAbsolutePath().normalize();
    }

    private String readStoredSha(String slug, Long releaseId) {
        try {
            Path path = shaPath(slug, releaseId);
            return Files.exists(path) ? Files.readString(path, StandardCharsets.US_ASCII).trim() : null;
        } catch (IOException exception) {
            throw new RunnerException("RELEASE_STATE_READ_FAILED", "Unable to read retained release state", exception);
        }
    }

    private void writeStoredSha(Path releaseDirectory, String sha) throws IOException {
        Path path = releaseDirectory.resolve(".joyhub-release.sha256");
        Files.writeString(path, sha, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // Cleanup failure must not hide the deployment failure that triggered it.
                }
            });
        } catch (IOException ignored) {
            // Cleanup failure must not hide the deployment failure that triggered it.
        }
    }
}
