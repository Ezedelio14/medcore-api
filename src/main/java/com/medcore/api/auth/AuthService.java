package com.medcore.api.auth;

import com.medcore.api.audit.AuditService;
import com.medcore.api.auth.dto.LoginResponse;
import com.medcore.api.auth.dto.RegisterRequest;
import com.medcore.api.auth.dto.TokenResponse;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ConflictException;
import com.medcore.api.common.exception.ResourceNotFoundException;
import com.medcore.api.common.exception.UnauthorizedException;
import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.integration.email.EmailSender;
import com.medcore.api.patient.Patient;
import com.medcore.api.patient.PatientRepository;
import com.medcore.api.security.JwtService;
import com.medcore.api.security.TokenStore;
import com.medcore.api.security.TokenStore.Purpose;
import com.medcore.api.security.UserPrincipal;
import com.medcore.api.user.Role;
import com.medcore.api.user.User;
import com.medcore.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final MedcoreProperties properties;

    /** Pre-computed hash used to keep login timing constant when the e-mail does not exist. */
    private String dummyHash;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_IN_USE", "An account with this e-mail already exists");
        }
        User user = userRepository.save(new User(request.email(), passwordEncoder.encode(request.password()), Role.PATIENT));

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setPhone(request.phone());
        patientRepository.save(patient);

        sendVerificationEmail(user);
        auditService.record(user.getId(), "AUTH_REGISTER", "users", user.getId(), Map.of());
        return user;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash());
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.record(user.getId(), "AUTH_LOGIN_FAILED", "users", user.getId(), Map.of());
            throw invalidCredentials();
        }
        if (!user.canAuthenticate()) {
            throw new UnauthorizedException("ACCOUNT_DISABLED", "This account is disabled");
        }
        if (user.isTwoFactorEnabled()) {
            String challenge = TokenStore.randomToken();
            String code = TokenStore.randomNumericCode(6);
            tokenStore.store(Purpose.TWO_FACTOR, challenge, user.getId() + ":" + code, properties.security().twoFactorCodeTtl());
            emailSender.send(user.getEmail(), "Your MedCore sign-in code",
                    "Your verification code is %s. It expires in %d minutes."
                            .formatted(code, properties.security().twoFactorCodeTtl().toMinutes()));
            return LoginResponse.challenge(challenge);
        }
        auditService.record(user.getId(), "AUTH_LOGIN", "users", user.getId(), Map.of());
        return LoginResponse.authenticated(issueTokens(user));
    }

    /** Completes a 2FA login. The challenge is single-use: a wrong code requires logging in again. */
    @Transactional(readOnly = true)
    public TokenResponse completeTwoFactorLogin(String challengeToken, String code) {
        String stored = tokenStore.consume(Purpose.TWO_FACTOR, challengeToken)
                .orElseThrow(() -> new UnauthorizedException("INVALID_2FA_CHALLENGE", "2FA challenge expired or invalid"));
        String[] parts = stored.split(":", 2);
        if (!MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("INVALID_2FA_CODE", "Invalid verification code");
        }
        User user = activeUser(UUID.fromString(parts[0]));
        auditService.record(user.getId(), "AUTH_LOGIN_2FA", "users", user.getId(), Map.of());
        return issueTokens(user);
    }

    /** Refresh token rotation: the presented token is consumed and a new pair is issued. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        UUID userId = tokenStore.consume(Purpose.REFRESH, refreshToken).map(UUID::fromString)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token expired or invalid"));
        return issueTokens(activeUser(userId));
    }

    public void logout(UserPrincipal principal, String refreshToken) {
        if (refreshToken != null) {
            tokenStore.revoke(Purpose.REFRESH, refreshToken);
        }
        tokenStore.revokeAccessToken(principal.tokenId(), Duration.between(Instant.now(), principal.tokenExpiresAt()));
        auditService.record(principal.id(), "AUTH_LOGOUT", "users", principal.id(), Map.of());
    }

    /** Always succeeds from the caller's point of view, to avoid revealing which e-mails are registered. */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).filter(User::canAuthenticate).ifPresent(user -> {
            String token = TokenStore.randomToken();
            tokenStore.store(Purpose.PASSWORD_RESET, token, user.getId().toString(), properties.security().passwordResetTtl());
            try {
                emailSender.send(user.getEmail(), "Reset your MedCore password",
                        "Reset your password here (valid for %d minutes): %s/reset-password?token=%s"
                                .formatted(properties.security().passwordResetTtl().toMinutes(), properties.frontendUrl(), token));
            } catch (RuntimeException e) {
                log.warn("Could not send password reset e-mail to user {}", user.getId(), e);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        UUID userId = tokenStore.consume(Purpose.PASSWORD_RESET, token).map(UUID::fromString)
                .orElseThrow(() -> new BusinessException("INVALID_RESET_TOKEN", "Reset link expired or invalid"));
        User user = activeUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        auditService.record(userId, "AUTH_PASSWORD_RESET", "users", userId, Map.of());
    }

    @Transactional
    public void verifyEmail(String token) {
        UUID userId = tokenStore.consume(Purpose.EMAIL_VERIFICATION, token).map(UUID::fromString)
                .orElseThrow(() -> new BusinessException("INVALID_VERIFICATION_TOKEN", "Verification link expired or invalid"));
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId))
                .markEmailVerified();
    }

    @Transactional(readOnly = true)
    public void resendVerification(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new ConflictException("EMAIL_ALREADY_VERIFIED", "E-mail is already verified");
        }
        sendVerificationEmail(user);
    }

    @Transactional
    public User setTwoFactor(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (enabled && !user.isEmailVerified()) {
            throw new BusinessException("EMAIL_NOT_VERIFIED", "Verify your e-mail before enabling 2FA");
        }
        user.setTwoFactorEnabled(enabled);
        auditService.record(userId, enabled ? "AUTH_2FA_ENABLED" : "AUTH_2FA_DISABLED", "users", userId, Map.of());
        return user;
    }

    @Transactional(readOnly = true)
    public User me(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = TokenStore.randomToken();
        tokenStore.store(Purpose.REFRESH, refreshToken, user.getId().toString(), properties.security().jwt().refreshTokenTtl());
        return new TokenResponse(jwtService.issueAccessToken(user), refreshToken, "Bearer",
                jwtService.accessTokenTtl().toSeconds(), user.getId(), user.getRole());
    }

    /** Best effort: a mail outage must not block registration; the user can request a new link. */
    private void sendVerificationEmail(User user) {
        String token = TokenStore.randomToken();
        tokenStore.store(Purpose.EMAIL_VERIFICATION, token, user.getId().toString(), properties.security().emailVerificationTtl());
        try {
            emailSender.send(user.getEmail(), "Confirm your MedCore e-mail",
                    "Confirm your e-mail address: %s/verify-email?token=%s".formatted(properties.frontendUrl(), token));
        } catch (RuntimeException e) {
            log.warn("Could not send verification e-mail to user {}", user.getId(), e);
        }
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Account no longer exists"));
        if (!user.canAuthenticate()) {
            throw new UnauthorizedException("ACCOUNT_DISABLED", "This account is disabled");
        }
        return user;
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid e-mail or password");
    }
}
