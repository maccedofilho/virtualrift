package com.virtualrift.gateway.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.virtualrift.gateway.config.GatewayConfig;
import com.virtualrift.gateway.config.JwtConfig;
import com.virtualrift.gateway.dto.ProblemDetail;
import com.virtualrift.gateway.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JWT validation filter for the gateway.
 * validates JWT tokens and extracts claims to be forwarded to downstream services.
 *
 * this filter:
 * - validates JWT tokens using RS256
 * - checks token against denylist (logged out tokens)
 * - extracts tenant_id, user_id, and roles from token
 * - adds claims as headers for downstream services
 * - allows public paths without authentication
 */
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health",
            "/actuator/health",
            "/api/v1/auth/token",
            "/api/v1/auth/refresh",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-Roles";

    private final JwtConfig.JwtValidator jwtValidator;
    private final ReactiveTokenDenylist tokenDenylist;
    private final GatewayConfig gatewayConfig;

    public JwtValidationFilter(JwtConfig.JwtValidator jwtValidator,
                               ReactiveTokenDenylist tokenDenylist,
                               GatewayConfig gatewayConfig) {
        this.jwtValidator = jwtValidator;
        this.tokenDenylist = tokenDenylist;
        this.gatewayConfig = gatewayConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (token == null) {
            log.debug("Missing Authorization header for path: {}", path);
            return ResponseUtil.writeProblemDetail(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    ProblemDetail.unauthorized("Missing or invalid Authorization header", path)
            );
        }

        return tokenDenylist.isDenied(token)
                .flatMap(isDenied -> {
                    if (isDenied) {
                        log.debug("Token is denylisted");
                        return ResponseUtil.writeProblemDetail(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                ProblemDetail.unauthorized("Token has been revoked", path)
                        );
                    }

                    try {
                        jwtValidator.validate(token);
                        return proceedWithValidToken(exchange, chain, token);
                    } catch (JWTVerificationException e) {
                        log.debug("Invalid token for path {}: {}", path, e.getMessage());
                        return ResponseUtil.writeProblemDetail(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                ProblemDetail.unauthorized("Invalid or expired token", path)
                        );
                    }
                });
    }

    private Mono<Void> proceedWithValidToken(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               String token) {
        try {
            UUID tenantId = jwtValidator.extractTenantId(token);
            UUID userId = jwtValidator.extractUserId(token);
            Set<String> roles = jwtValidator.extractRoles(token);

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(r -> r
                            .header(TENANT_ID_HEADER, tenantId.toString())
                            .header(USER_ID_HEADER, userId.toString())
                            .header(ROLES_HEADER, String.join(",", roles)))
                    .build();

            log.debug("Authorized request: tenantId={}, userId={}, path={}", tenantId, userId,
                    exchange.getRequest().getPath().value());

            return chain.filter(mutatedExchange);

        } catch (IllegalArgumentException e) {
            log.debug("Failed to extract claims from token: {}", e.getMessage());
            return ResponseUtil.writeProblemDetail(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    ProblemDetail.unauthorized("Invalid token claims", exchange.getRequest().getPath().value())
            );
        }
    }

    private String extractToken(ServerWebExchange exchange) {
        List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }

        String authHeader = authHeaders.get(0);
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        if (!authHeader.toLowerCase().startsWith(BEARER_PREFIX.toLowerCase())) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length()).trim();
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        if (path.startsWith("/actuator/")) {
            return true;
        }

        for (String publicPath : gatewayConfig.getSecurity().getPublicPaths()) {
            if (publicPath.endsWith("/**")) {
                String prefix = publicPath.substring(0, publicPath.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (path.equals(publicPath)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
