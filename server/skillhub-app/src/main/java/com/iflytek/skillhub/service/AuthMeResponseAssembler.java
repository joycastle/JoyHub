package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.AuthMeResponse;
import org.springframework.stereotype.Service;

/**
 * Builds the current-user API response with account capabilities derived from
 * authoritative backend state.
 */
@Service
public class AuthMeResponseAssembler {

    private final LocalCredentialRepository localCredentialRepository;

    public AuthMeResponseAssembler(LocalCredentialRepository localCredentialRepository) {
        this.localCredentialRepository = localCredentialRepository;
    }

    public AuthMeResponse from(PlatformPrincipal principal) {
        return AuthMeResponse.from(
                principal,
                localCredentialRepository.existsByUserId(principal.userId())
        );
    }
}
