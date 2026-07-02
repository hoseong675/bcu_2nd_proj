package com.bcu.pcquote.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /api/admin/** 보호 필터.
 * 요청 헤더 X-Admin-Token 이 설정된 시크릿(admin.token)과 일치할 때만 통과.
 * 시크릿 미설정 시에는 관리자 엔드포인트를 전면 차단(fail-closed).
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private final String adminToken;

    public AdminAuthFilter(@Value("${admin.token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/admin/")) {
            String provided = req.getHeader("X-Admin-Token");
            if (adminToken == null || adminToken.isBlank() || !adminToken.equals(provided)) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"unauthorized\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
