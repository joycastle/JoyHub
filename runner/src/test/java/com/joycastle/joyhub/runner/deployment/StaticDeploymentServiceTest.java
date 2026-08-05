package com.joycastle.joyhub.runner.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joycastle.joyhub.runner.api.RunnerDeploymentRequest;
import com.joycastle.joyhub.runner.api.RunnerOfflineRequest;
import com.joycastle.joyhub.runner.api.RunnerSwitchRequest;
import com.joycastle.joyhub.runner.config.RunnerProperties;
import com.joycastle.joyhub.runner.exception.RunnerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticDeploymentServiceTest {
    @TempDir
    Path tempDir;

    private RunnerProperties properties;
    private TestVerifier verifier;
    private StaticDeploymentService service;

    @BeforeEach
    void setUp() {
        properties = new RunnerProperties();
        properties.setDataPath(tempDir);
        properties.setVerificationOrigin("http://static-host");
        properties.setPathPrefix("/apps");
        properties.setMaxZipSize(50L * 1024L * 1024L);
        properties.setMaxExpandedSize(200L * 1024L * 1024L);
        properties.setMaxSingleFileSize(20L * 1024L * 1024L);
        properties.setMaxFileCount(2000);
        properties.setMaxCompressionRatio(100);
        verifier = new TestVerifier();
        service = new StaticDeploymentService(properties, verifier);
    }

    @Test
    void deploysUpdatesRollsBackAndRestoresFromPersistentState() throws Exception {
        byte[] v1 = zip("index.html", "version-one");
        byte[] v2 = zip("index.html", "version-two");

        assertThat(service.deploy(deployRequest(1L, 11L, v1), v1).success()).isTrue();
        assertThat(service.state("demo-app").currentReleaseId()).isEqualTo("11");
        assertThat(Files.readString(tempDir.resolve("published/demo-app/index.html"))).isEqualTo("version-one");

        assertThat(service.deploy(deployRequest(2L, 12L, v2), v2).success()).isTrue();
        assertThat(service.state("demo-app").currentReleaseId()).isEqualTo("12");

        service.rollback(switchRequest(3L, 11L));
        assertThat(Files.readString(tempDir.resolve("published/demo-app/index.html"))).isEqualTo("version-one");

        service.offline(new RunnerOfflineRequest(4L, 1L, "demo-app"));
        assertThat(service.state("demo-app").online()).isFalse();

        StaticDeploymentService restarted = new StaticDeploymentService(properties, verifier);
        restarted.restore(switchRequest(5L, 12L));
        assertThat(restarted.state("demo-app").currentReleaseId()).isEqualTo("12");
        assertThat(Files.readString(tempDir.resolve("published/demo-app/index.html"))).isEqualTo("version-two");
    }

    @Test
    void verificationFailureRestoresPreviouslyServingVersion() throws Exception {
        byte[] v1 = zip("index.html", "good");
        byte[] v2 = zip("index.html", "candidate");
        service.deploy(deployRequest(1L, 11L, v1), v1);

        verifier.failNext = true;
        assertThatThrownBy(() -> service.deploy(deployRequest(2L, 12L, v2), v2))
                .isInstanceOf(RunnerException.class)
                .hasMessageContaining("verification");

        assertThat(service.state("demo-app").currentReleaseId()).isEqualTo("11");
        assertThat(Files.readString(tempDir.resolve("published/demo-app/index.html"))).isEqualTo("good");
    }

    @Test
    void rejectsTraversalSymlinksMissingIndexAndExpandedSizeOverflow() throws Exception {
        assertRejected(zip("../escape.txt", "bad"), "ZIP_PATH_TRAVERSAL");
        assertRejected(symlinkZip(), "ZIP_SYMLINK_REJECTED");
        assertRejected(zip("app.html", "no index"), "INDEX_HTML_REQUIRED");

        properties.setMaxExpandedSize(4);
        assertRejected(zip("index.html", "too large"), "ZIP_EXPANDED_SIZE_EXCEEDED");
        assertThat(Files.exists(tempDir.resolve("escape.txt"))).isFalse();
    }

    @Test
    void rejectsArtifactHashMismatchAndImmutableReleaseConflict() throws Exception {
        byte[] v1 = zip("index.html", "one");
        byte[] v2 = zip("index.html", "two");
        RunnerDeploymentRequest badHash = new RunnerDeploymentRequest(
                1L, 1L, 11L, "demo-app", "v1", "0".repeat(64), "http://localhost/apps/demo-app/");
        assertThatThrownBy(() -> service.deploy(badHash, v1))
                .extracting(exception -> ((RunnerException) exception).code())
                .isEqualTo("ARTIFACT_SHA256_MISMATCH");

        service.deploy(deployRequest(2L, 11L, v1), v1);
        assertThatThrownBy(() -> service.deploy(deployRequest(3L, 11L, v2), v2))
                .extracting(exception -> ((RunnerException) exception).code())
                .isEqualTo("IMMUTABLE_RELEASE_CONFLICT");
    }

    private void assertRejected(byte[] zip, String code) {
        assertThatThrownBy(() -> service.deploy(deployRequest(99L, 99L, zip), zip))
                .isInstanceOfSatisfying(RunnerException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private RunnerDeploymentRequest deployRequest(Long jobId, Long releaseId, byte[] zip) {
        return new RunnerDeploymentRequest(
                jobId,
                1L,
                releaseId,
                "demo-app",
                "v" + releaseId,
                sha256(zip),
                "http://localhost/apps/demo-app/"
        );
    }

    private RunnerSwitchRequest switchRequest(Long jobId, Long releaseId) {
        return new RunnerSwitchRequest(
                jobId, 1L, releaseId, "demo-app", "http://localhost/apps/demo-app/");
    }

    private byte[] zip(String name, String content) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            zip.putArchiveEntry(entry);
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private byte[] symlinkZip() throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            ZipArchiveEntry index = new ZipArchiveEntry("index.html");
            zip.putArchiveEntry(index);
            zip.write("ok".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
            ZipArchiveEntry link = new UnixZipArchiveEntry("link.js");
            link.setUnixMode(UnixStat.LINK_FLAG | 0777);
            zip.putArchiveEntry(link);
            zip.write("index.html".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class TestVerifier implements StaticDeploymentVerifier {
        private boolean failNext;

        @Override
        public void verifyAvailable(String slug) {
            if (failNext) {
                failNext = false;
                throw new RunnerException("STATIC_VERIFICATION_FAILED", "Static host verification failed");
            }
        }
    }

    private static final class UnixZipArchiveEntry extends ZipArchiveEntry {
        private UnixZipArchiveEntry(String name) {
            super(name);
            setPlatform(PLATFORM_UNIX);
        }
    }
}
