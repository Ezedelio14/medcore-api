package com.medcore.api.user;

import com.medcore.api.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    public User(String email, String passwordHash, Role role) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.PENDING_VERIFICATION;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        if (status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /** Users may sign in before verifying their email but not once suspended or deleted. */
    public boolean canAuthenticate() {
        return status == UserStatus.ACTIVE || status == UserStatus.PENDING_VERIFICATION;
    }
}
