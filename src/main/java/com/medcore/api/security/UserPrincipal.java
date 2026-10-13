package com.medcore.api.security;

import com.medcore.api.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Authenticated caller, reconstructed from the JWT on every request (no DB hit). */
public record UserPrincipal(UUID id, String email, Role role, String tokenId, Instant tokenExpiresAt) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean hasRole(Role candidate) {
        return role == candidate;
    }

    public boolean isStaff() {
        return role == Role.RECEPTIONIST || role == Role.CLINIC_ADMIN;
    }
}
