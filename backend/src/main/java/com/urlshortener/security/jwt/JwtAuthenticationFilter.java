package com.urlshortener.security.jwt;

import com.urlshortener.entity.UserRole;
import com.urlshortener.exception.InvalidTokenException;
import com.urlshortener.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests bearing a valid "Authorization: Bearer <access-token>" header.
 * Deliberately does not reject requests on a missing/invalid token here - it just leaves
 * the SecurityContext empty, so anonymous access to public endpoints (e.g. /{shortCode})
 * keeps working; endpoints that require authentication reject unauthenticated requests
 * further down the filter chain via the configured entry point.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                if (jwtTokenProvider.isAccessToken(claims)) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                        jwtTokenProvider.getUserId(claims),
                        claims.getSubject(),
                        UserRole.valueOf(jwtTokenProvider.getRole(claims)));
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (InvalidTokenException e) {
                log.debug("Rejected invalid JWT: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
