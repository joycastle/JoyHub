package com.iflytek.skillhub.mcp;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.Hidden
@RestController
public class McpDownloadController {
    private final McpDownloadService downloads;
    public McpDownloadController(McpDownloadService downloads) { this.downloads = downloads; }

    @GetMapping("/api/mcp/downloads/{ticket}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String ticket) {
        var file = downloads.redeem(ticket);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Referrer-Policy", "no-referrer").header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(file.size()).body(new InputStreamResource(file.stream()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, String>> rejected(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("error", exception.getStatusCode().value() == 409
                        ? "Resource changed; request a new download" : "Download unavailable or access denied"));
    }
}
