package com.joycastle.joyhub.runner.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "joyhub.runner")
public class RunnerProperties {
    private String token;
    private Path dataPath;
    private String verificationOrigin;
    private String pathPrefix;
    private long maxZipSize;
    private long maxExpandedSize;
    private long maxSingleFileSize;
    private int maxFileCount;
    private int maxCompressionRatio;

    public String verificationUrl(String slug) {
        String origin = verificationOrigin.endsWith("/")
                ? verificationOrigin.substring(0, verificationOrigin.length() - 1) : verificationOrigin;
        String prefix = pathPrefix.startsWith("/") ? pathPrefix : "/" + pathPrefix;
        prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return origin + prefix + "/" + slug + "/";
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Path getDataPath() { return dataPath; }
    public void setDataPath(Path dataPath) { this.dataPath = dataPath; }
    public String getVerificationOrigin() { return verificationOrigin; }
    public void setVerificationOrigin(String verificationOrigin) { this.verificationOrigin = verificationOrigin; }
    public String getPathPrefix() { return pathPrefix; }
    public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
    public long getMaxZipSize() { return maxZipSize; }
    public void setMaxZipSize(long maxZipSize) { this.maxZipSize = maxZipSize; }
    public long getMaxExpandedSize() { return maxExpandedSize; }
    public void setMaxExpandedSize(long maxExpandedSize) { this.maxExpandedSize = maxExpandedSize; }
    public long getMaxSingleFileSize() { return maxSingleFileSize; }
    public void setMaxSingleFileSize(long maxSingleFileSize) { this.maxSingleFileSize = maxSingleFileSize; }
    public int getMaxFileCount() { return maxFileCount; }
    public void setMaxFileCount(int maxFileCount) { this.maxFileCount = maxFileCount; }
    public int getMaxCompressionRatio() { return maxCompressionRatio; }
    public void setMaxCompressionRatio(int maxCompressionRatio) { this.maxCompressionRatio = maxCompressionRatio; }
}
