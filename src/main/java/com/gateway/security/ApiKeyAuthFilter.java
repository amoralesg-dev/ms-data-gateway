package com.gateway.security;

import com.gateway.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final GatewayProperties gatewayProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(gatewayProperties.getSecurity().getApiKeyHeader());
        String adminKey = request.getHeader(gatewayProperties.getSecurity().getAdminKeyHeader());

        if (adminKey != null && gatewayProperties.getSecurity().getAdminKeys().contains(adminKey)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "admin-client", null, List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_API_CLIENT")
                    ));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("ADMIN Key authentication successful for request: {}", request.getRequestURI());
        } else if (apiKey != null && gatewayProperties.getSecurity().getApiKeys().contains(apiKey)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("API Key authentication successful for request: {}", request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/v3/api-docs");
    }
}
