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

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String header = request.getHeader("Authorization");
        String token = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        String path = request.getRequestURI();

        // 1. Skip validation for public endpoints
        if (path.startsWith("/auth/") ||
            path.equals("/admin/login") ||
            path.startsWith("/public/") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Missing Token (401)
        if (token == null) {
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

            // 4. Null safety for attributes
            if (id != null) {
                request.setAttribute("userId", id);
            }
            if (type != null) {
                request.setAttribute("type", type);
            }

            // 5. Access Control (403)
            if (path.startsWith("/admin/") && !"ADMIN".equals(type)) {
                sendErrorResponse(response, "Access denied. Admin only.", 403);
                return;
            }

            // 6. Set Authentication in Context
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    id, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            sendErrorResponse(response, "Failed to parse authentication token", 401);
            return;
        }

        filterChain.doFilter(request, response);
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
