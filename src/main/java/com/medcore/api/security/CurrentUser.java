package com.medcore.api.security;

import com.medcore.api.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? Optional.of(principal) : Optional.empty();
    }

    public static UserPrincipal get() {
        return find().orElseThrow(() -> new UnauthorizedException("UNAUTHORIZED", "Authentication required"));
    }
}
