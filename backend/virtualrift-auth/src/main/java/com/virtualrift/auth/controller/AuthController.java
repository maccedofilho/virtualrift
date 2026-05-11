package com.virtualrift.auth.controller;

import com.virtualrift.auth.dto.AccountProfileResponse;
import com.virtualrift.auth.dto.LoginRequest;
import com.virtualrift.auth.dto.LoginResponse;
import com.virtualrift.auth.dto.RefreshTokenRequest;
import com.virtualrift.auth.service.AccountService;
import com.virtualrift.auth.service.LoginService;
import com.virtualrift.auth.service.OAuthLoginService;
import com.virtualrift.common.security.RoleAccess;
import com.virtualrift.common.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Autenticacao, renovacao de sessao e perfil do operador autenticado.")
public class AuthController {

    private final LoginService loginService;
    private final AccountService accountService;
    private final OAuthLoginService oAuthLoginService;

    public AuthController(LoginService loginService, AccountService accountService, OAuthLoginService oAuthLoginService) {
        this.loginService = loginService;
        this.accountService = accountService;
        this.oAuthLoginService = oAuthLoginService;
    }

    private void requireAnyRole(String rolesHeader, UserRole... allowedRoles) {
        if (!RoleAccess.hasAny(rolesHeader, allowedRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User role is not allowed to access this resource");
        }
    }

    @PostMapping("/token")
    @Operation(summary = "Gerar access token", description = "Login por e-mail e senha.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar sessao", description = "Rotaciona o refresh token e retorna novo par de credenciais.")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = loginService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerrar sessao", description = "Revoga access token e refresh token da sessao atual.")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }

        String refreshToken = request != null ? request.refreshToken() : null;
        loginService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Buscar perfil autenticado", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao para acessar o proprio perfil"),
            @ApiResponse(responseCode = "404", description = "Usuario autenticado nao encontrado")
    })
    public ResponseEntity<AccountProfileResponse> me(
            @RequestHeader("X-Roles") String rolesHeader,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Tenant-Id") UUID tenantId
    ) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        return ResponseEntity.ok(accountService.getProfile(userId, tenantId));
    }

    @GetMapping("/oauth/github/start")
    @Operation(
            summary = "Iniciar login com GitHub",
            description = "Redireciona para o GitHub e preserva o redirect_uri do frontend no state assinado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirecionado para o GitHub ou de volta ao frontend em caso de provider indisponivel"),
            @ApiResponse(responseCode = "400", description = "redirect_uri invalido")
    })
    public ResponseEntity<Void> startGitHubOAuth(
            @RequestParam("redirect_uri") @NotBlank String redirectUri
    ) {
        URI location = oAuthLoginService.buildGitHubStartRedirect(redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    @GetMapping("/oauth/github/callback")
    @Operation(
            summary = "Concluir login com GitHub",
            description = "Recebe o callback do GitHub, emite tokens do VirtualRift e redireciona de volta ao frontend."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirecionado de volta ao frontend com tokens ou erro"),
            @ApiResponse(responseCode = "400", description = "state invalido ou callback inconsistente")
    })
    public ResponseEntity<Void> completeGitHubOAuth(
            @RequestParam("state") String state,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error
    ) {
        URI location = oAuthLoginService.handleGitHubCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }
}
