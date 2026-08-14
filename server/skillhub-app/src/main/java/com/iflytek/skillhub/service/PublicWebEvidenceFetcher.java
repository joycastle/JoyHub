package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** Fetches one bounded public documentation page without cookies, scripts, or automatic redirects. */
@Service
public class PublicWebEvidenceFetcher {
    private static final int MAX_REDIRECTS = 2;
    private static final int MAX_BYTES = 1_000_000;
    private static final int MAX_CHARS = 36_000;
    private static final Pattern BLOCKED_ELEMENTS = Pattern.compile(
            "<(script|style|noscript|template|iframe)[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]+(?:name\\s*=\\s*[\"']description[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']"
                    + "|content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*name\\s*=\\s*[\"']description[\"'])[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;

    public PublicWebEvidenceFetcher() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    PublicWebEvidenceFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String fetch(String accessUrl) {
        URI current = parseAndValidate(accessUrl, false);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                        .header("User-Agent", "JoyHub-Documentation-Generator/1.0")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    close(response.body());
                    if (location == null || redirects == MAX_REDIRECTS) {
                        throw CatalogDomainException.badRequest("error.catalog.urlEvidence.fetchFailed");
                    }
                    URI next = current.resolve(location);
                    if ("https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(next.getScheme())) {
                        throw CatalogDomainException.badRequest("error.catalog.urlEvidence.invalid");
                    }
                    current = parseAndValidate(next.toString(), false);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    close(response.body());
                    throw CatalogDomainException.badRequest("error.catalog.urlEvidence.fetchFailed");
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!(contentType.startsWith("text/html") || contentType.startsWith("application/xhtml+xml")
                        || contentType.startsWith("text/plain"))) {
                    close(response.body());
                    throw CatalogDomainException.badRequest("error.catalog.urlEvidence.unsupportedContent");
                }
                String page = new String(readBounded(response.body()), StandardCharsets.UTF_8);
                String evidence = contentType.startsWith("text/plain") ? normalize(page) : extractHtml(page);
                if (evidence.isBlank()) {
                    throw CatalogDomainException.badRequest("error.catalog.urlEvidence.empty");
                }
                return "Public Tool page: " + current + "\n\n" + evidence;
            } catch (CatalogDomainException exception) {
                throw exception;
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw CatalogDomainException.badRequest("error.catalog.urlEvidence.fetchFailed");
            }
        }
        throw CatalogDomainException.badRequest("error.catalog.urlEvidence.fetchFailed");
    }

    static URI parseAndValidate(String value, boolean allowPrivateForTest) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ((!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
                    || host == null || host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null
                    || host.equalsIgnoreCase("localhost") || !host.contains(".")) {
                throw CatalogDomainException.badRequest("error.catalog.urlEvidence.invalid");
            }
            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {
                throw CatalogDomainException.badRequest("error.catalog.urlEvidence.invalid");
            }
            if (!allowPrivateForTest) {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (!isPublic(address)) throw CatalogDomainException.badRequest("error.catalog.urlEvidence.blocked");
                }
            }
            return uri;
        } catch (CatalogDomainException exception) {
            throw exception;
        } catch (URISyntaxException | IOException exception) {
            throw CatalogDomainException.badRequest("error.catalog.urlEvidence.invalid");
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0 && first != 10 && first != 127 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254) && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 168) && !(first == 192 && second == 0) && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc && !(first == 0xfe && (Byte.toUnsignedInt(bytes[1]) & 0xc0) == 0x80);
        }
        return false;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw CatalogDomainException.badRequest("error.catalog.urlEvidence.tooLarge");
            return bytes;
        }
    }

    private void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Best effort: the response has already been rejected.
        }
    }

    private String extractHtml(String html) {
        String stripped = BLOCKED_ELEMENTS.matcher(html).replaceAll(" ");
        String title = textGroup(TITLE.matcher(stripped));
        String description = textGroup(META_DESCRIPTION.matcher(stripped));
        String body = normalize(HtmlUtils.htmlUnescape(TAGS.matcher(stripped).replaceAll(" ")));
        return truncate((title.isBlank() ? "" : "Title: " + title + "\n")
                + (description.isBlank() ? "" : "Description: " + description + "\n") + body);
    }

    private String textGroup(Matcher matcher) {
        if (!matcher.find()) return "";
        for (int index = 1; index <= matcher.groupCount(); index++) {
            if (matcher.group(index) != null) return normalize(HtmlUtils.htmlUnescape(matcher.group(index)));
        }
        return "";
    }

    private String normalize(String text) {
        return truncate(text.replace('\u0000', ' ').replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim());
    }

    private String truncate(String text) {
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS) + "\n[truncated]";
    }
}
