package com.medcore.api.auth.dto;

import com.medcore.api.user.Role;
import com.medcore.api.user.User;
import com.medcore.api.user.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Current user account")
public record MeResponse(UUID id, String email, Role role, UserStatus status, boolean emailVerified, boolean twoFactorEnabled) {

    public static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getEmail(), u.getRole(), u.getStatus(), u.isEmailVerified(), u.isTwoFactorEnabled());
    }
}
