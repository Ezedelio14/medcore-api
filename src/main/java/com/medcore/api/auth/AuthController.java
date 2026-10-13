package com.medcore.api.auth;

import com.medcore.api.auth.dto.ForgotPasswordRequest;
import com.medcore.api.auth.dto.LoginRequest;
import com.medcore.api.auth.dto.LoginResponse;
import com.medcore.api.auth.dto.MeResponse;
import com.medcore.api.auth.dto.RefreshTokenRequest;
import com.medcore.api.auth.dto.RegisterRequest;
import com.medcore.api.auth.dto.ResetPasswordRequest;
import com.medcore.api.auth.dto.TokenResponse;
import com.medcore.api.auth.dto.TwoFactorLoginRequest;
import com.medcore.api.auth.dto.TwoFactorSettingsRequest;
import com.medcore.api.auth.dto.VerifyEmailRequest;
import com.medcore.api.config.OpenApiConfig;
import com.medcore.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, tokens, password reset, e-mail verification and 2FA")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a patient account", description = "Creates the user and patient profile and sends a verification e-mail.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "E-mail already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public MeResponse register(@Valid @RequestBody RegisterRequest request) {
        return MeResponse.from(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns tokens, or a 2FA challenge when the account has 2FA enabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated or 2FA challenge issued"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or disabled account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/login/2fa")
    @Operation(summary = "Complete a 2FA login", description = "Exchanges the challenge and the e-mailed 6-digit code for tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid/expired challenge or wrong code",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TokenResponse loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.completeTwoFactorLogin(request.challengeToken(), request.code());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Rotates the refresh token: the presented token becomes invalid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair"),
            @ApiResponse(responseCode = "401", description = "Refresh token expired, reused or invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Log out", description = "Revokes the current access token and the given refresh token.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(CurrentUser.get(), request.refreshToken());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset",
            description = "Always returns 202, whether or not the e-mail exists, to prevent account enumeration.")
    @ApiResponse(responseCode = "202", description = "Reset e-mail sent if the account exists")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset password with an e-mailed token")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "422", description = "Token expired or invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify e-mail address with an e-mailed token")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail verified"),
            @ApiResponse(responseCode = "422", description = "Token expired or invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    @PostMapping("/email/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Resend the verification e-mail")
    @ApiResponse(responseCode = "202", description = "Verification e-mail sent")
    public void resendVerification() {
        authService.resendVerification(CurrentUser.get().id());
    }

    @PutMapping("/2fa")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Enable or disable two-factor authentication", description = "Requires a verified e-mail to enable.")
    @ApiResponse(responseCode = "200", description = "Updated account")
    public MeResponse twoFactor(@Valid @RequestBody TwoFactorSettingsRequest request) {
        return MeResponse.from(authService.setTwoFactor(CurrentUser.get().id(), request.enabled()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Current account")
    @ApiResponse(responseCode = "200", description = "Current account")
    public MeResponse me() {
        return MeResponse.from(authService.me(CurrentUser.get().id()));
    }
}
