package com.bookstore.auth.config;

import com.bookstore.auth.domain.User;
import com.bookstore.auth.service.AuthService;
import com.bookstore.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT Authentication Filter.
 * Intercepts requests to validate JWT tokens and set authentication context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwtService, @Lazy AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");
        String token = null;
        String correlationId = getOrCreateCorrelationId(request);

        try {
            MDC.put("correlationId", correlationId);

            // Extract token from header
            if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
                token = requestTokenHeader.substring(7);

                // Validate token
                AuthService.TokenValidationResult validationResult = authService.validateToken(token, correlationId);

                if (validationResult.isValid()) {
                    User user = validationResult.getUser();

                    // Create authentication object
                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    logger.debug("[{}] JWT authentication successful for user: {}", correlationId, user.getEmail());
                } else {
                    logger.warn("[{}] JWT authentication failed: {}", correlationId, validationResult.getErrorMessage());
                }
            }

        } catch (Exception e) {
            logger.error("[{}] Error during JWT authentication: {}", correlationId, e.getMessage(), e);
            SecurityContextHolder.clearContext();
        } finally {
            MDC.remove("correlationId");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Get or create correlation ID from request headers.
     */
    private String getOrCreateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }
}