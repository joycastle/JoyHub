package com.iflytek.skillhub.controller.authorization;

import com.iflytek.skillhub.auth.authorization.JoyHubOAuthSettings;
import com.iflytek.skillhub.auth.authorization.OAuthClientRegistrationService;
import io.swagger.v3.oas.annotations.Hidden;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** User consent belongs to the shared authorization server and works for every OAuth client. */
@Hidden
@RestController
public class OAuthConsentController {
    private static final Map<String, String> LABELS = Map.of(
            "resource:read", "搜索、查看和下载你有权限访问的资源",
            "skill:read", "查询和下载你有权限访问的 Skill",
            "skill:publish", "以你的身份发布 Skill（仍受原有发布权限限制）",
            "offline_access", "保持连接，在授权有效期内自动续期");
    private final RegisteredClientRepository clients;
    public OAuthConsentController(RegisteredClientRepository clients) { this.clients = clients; }

    @GetMapping(value = "/oauth2/consent", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> consent(@RequestParam("client_id") String clientId, @RequestParam String state,
                                          @RequestParam String scope, @AuthenticationPrincipal PlatformPrincipal principal) {
        var client = clients.findByClientId(clientId);
        if (client == null || principal == null) return ResponseEntity.badRequest().body("Invalid authorization request");
        StringBuilder permissions = new StringBuilder();
        for (String value : scope.split(" ")) {
            if (!client.getScopes().contains(value) || !LABELS.containsKey(value)) continue;
            permissions.append("<label><input type='checkbox' name='scope' checked value='").append(escape(value))
                    .append("'> ").append(escape(LABELS.get(value))).append("</label>");
        }
        String html = """
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>连接 JoyHub</title><style>body{margin:0;background:#f7f8fb;font:16px/1.7 system-ui,sans-serif;color:#182230}
                main{max-width:480px;margin:10vh auto;padding:32px;background:white;border:1px solid #e6e9ef;border-radius:20px}
                h1{font-size:26px}label{display:block;margin:18px 0}button{padding:12px 22px;border:0;border-radius:10px;background:#2563eb;color:white;font-size:16px;cursor:pointer}
                p,small{color:#526173}.name{color:#182230}a{margin-left:20px;color:#526173}input{accent-color:#2563eb}</style>
                <main><strong>JoyHub</strong><h1>允许连接你的账号？</h1><p>客户端 <strong class="name">%s</strong> 请求以下权限。</p>
                <p>当前账号：%s</p><form method="post" action="/oauth2/authorize"><input type="hidden" name="client_id" value="%s">
                <input type="hidden" name="state" value="%s">%s<button type="submit">授权并连接</button>
                <a href="/">取消并返回 JoyHub</a></form><p><small>连接后仍按你的部门和资源权限访问。授权不会赋予额外的管理权限。</small></p></main></html>
                """.formatted(escape(client.getClientName()), escape(principal.displayName()), escape(clientId), escape(state), permissions);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
                .body(html);
    }

    private static String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
