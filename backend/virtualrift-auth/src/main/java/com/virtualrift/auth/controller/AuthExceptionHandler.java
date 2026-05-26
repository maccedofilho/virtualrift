package com.virtualrift.auth.controller;

import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidCredentialsException;
import com.virtualrift.auth.exception.InvalidPasswordException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.exception.OnboardingConflictException;
import com.virtualrift.auth.exception.OnboardingDisabledException;
import com.virtualrift.auth.exception.OnboardingProvisioningException;
import com.virtualrift.auth.exception.OAuthCallbackException;
import com.virtualrift.auth.exception.OAuthConfigurationException;
import com.virtualrift.auth.exception.OAuthRedirectUriException;
import com.virtualrift.auth.exception.OAuthUserProvisioningException;
import com.virtualrift.auth.exception.UserDeletedException;
import com.virtualrift.auth.exception.UserPendingVerificationException;
import com.virtualrift.auth.exception.UserSuspendedException;
import com.virtualrift.auth.exception.WorkspaceInvitationConflictException;
import com.virtualrift.auth.exception.WorkspaceInvitationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ProblemDetail handleInvalidPassword(InvalidPasswordException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid password", exception.getMessage());
    }

    @ExceptionHandler({InvalidTokenException.class, ExpiredTokenException.class})
    ProblemDetail handleInvalidToken(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler({UserPendingVerificationException.class, UserSuspendedException.class, UserDeletedException.class})
    ProblemDetail handleUserStatus(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(OAuthRedirectUriException.class)
    ProblemDetail handleInvalidRedirect(OAuthRedirectUriException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid redirect URI", exception.getMessage());
    }

    @ExceptionHandler(OAuthCallbackException.class)
    ProblemDetail handleOAuthCallback(OAuthCallbackException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid OAuth callback", exception.getMessage());
    }

    @ExceptionHandler(OAuthConfigurationException.class)
    ProblemDetail handleOAuthConfiguration(OAuthConfigurationException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "OAuth not configured", exception.getMessage());
    }

    @ExceptionHandler(OAuthUserProvisioningException.class)
    ProblemDetail handleOAuthProvisioning(OAuthUserProvisioningException exception) {
        return problem(HttpStatus.FORBIDDEN, "OAuth user is not provisioned", exception.getMessage());
    }

    @ExceptionHandler(OnboardingConflictException.class)
    ProblemDetail handleOnboardingConflict(OnboardingConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Onboarding conflict", exception.getMessage());
    }

    @ExceptionHandler(OnboardingDisabledException.class)
    ProblemDetail handleOnboardingDisabled(OnboardingDisabledException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Onboarding disabled", exception.getMessage());
    }

    @ExceptionHandler(OnboardingProvisioningException.class)
    ProblemDetail handleOnboardingProvisioning(OnboardingProvisioningException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Onboarding provisioning failed", exception.getMessage());
    }

    @ExceptionHandler(WorkspaceInvitationNotFoundException.class)
    ProblemDetail handleInvitationNotFound(WorkspaceInvitationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Workspace invitation not found", exception.getMessage());
    }

    @ExceptionHandler(WorkspaceInvitationConflictException.class)
    ProblemDetail handleInvitationConflict(WorkspaceInvitationConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Workspace invitation is not available", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
