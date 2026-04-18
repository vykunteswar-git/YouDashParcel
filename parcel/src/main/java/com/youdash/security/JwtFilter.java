package com.youdash.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.bean.ApiResponse;
import com.youdash.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String header = request.getHeader("Authorization");
        String token = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        }

        // Path without context path; avoids empty servletPath + pathInfo quirks on some containers.
        String path = PATH_HELPER.getPathWithinApplication(request);

        // 1. Skip validation for public endpoints
        if (path.startsWith("/auth/") ||
            path.startsWith("/rider-auth/") ||
            path.startsWith("/rider/auth/") ||
            path.equals("/admin/login") ||
            path.startsWith("/public/") ||
            path.equals("/payments/webhook") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            ("POST".equalsIgnoreCase(request.getMethod()) && path.equals("/riders"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Missing Token (401)
        if (token == null || token.isEmpty()) {
            sendErrorResponse(response, "Authorization token is missing", 401);
            return;
        }

        // 3. Validate Token (401)
        if (!jwtUtil.validateToken(token)) {
            sendErrorResponse(response, "Invalid or expired token", 401);
            return;
        }

        try {
            Long id = jwtUtil.extractId(token);
            String type = jwtUtil.extractType(token);

            if (id == null) {
                sendErrorResponse(response, "Invalid token: missing subject id", 401);
                return;
            }

            // 4. Null safety for attributes
            request.setAttribute("userId", id);
            if (type != null) {
                request.setAttribute("type", type);
            }

            // 5. Access Control (403)
            if (path.startsWith("/admin/") && !"ADMIN".equals(type)) {
                sendErrorResponse(response, "Access denied. Admin only.", 403);
                return;
            }

            // 6. Set Authentication in Context (authorities help any ROLE_* matchers; empty list caused 403 on some setups)
            List<GrantedAuthority> authorities = buildAuthorities(type);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    id, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            sendErrorResponse(response, "Failed to parse authentication token", 401);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> buildAuthorities(String type) {
        if (type == null || type.isBlank()) {
            return Collections.emptyList();
        }
        String role = type.trim().toUpperCase();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return List.of(new SimpleGrantedAuthority(role));
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        ApiResponse<Object> apiResponse = new ApiResponse<>();
        apiResponse.setMessage(message);
        apiResponse.setMessageKey("ERROR");
        apiResponse.setSuccess(false);
        apiResponse.setStatus(status);

        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
    }
}
