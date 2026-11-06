package com.edusync.edusync.backend.auth.filter;

import com.edusync.edusync.backend.auth.service.JwtService;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }

        String path = request.getServletPath();

        return path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extractEmail(token);

            if (email != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(token, userDetails)) {

                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() ->
                                    new UsernameNotFoundException("User not found")
                            );

                    if (!jwtService.isTokenVersionValid(token, user)) {

                        SecurityContextHolder.clearContext();

                        response.setStatus(
                                HttpServletResponse.SC_UNAUTHORIZED
                        );

                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");

                        response.getWriter().write(
                                "{\"message\":\"Invalid or revoked token\"}"
                        );

                        return;
                    }

                    boolean mustChangePassword =
                            Boolean.TRUE.equals(user.getMustChangePassword());

                    boolean isChangePasswordRequest =
                            HttpMethod.POST.matches(request.getMethod())
                                    && request.getServletPath()
                                    .equals("/api/auth/change-password");

                    boolean isMeRequest =
                            HttpMethod.GET.matches(request.getMethod())
                                    && request.getServletPath()
                                    .equals("/api/auth/me");

                    if (mustChangePassword
                            && !isChangePasswordRequest
                            && !isMeRequest) {

                        SecurityContextHolder.clearContext();

                        response.setStatus(
                                HttpServletResponse.SC_FORBIDDEN
                        );

                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");

                        response.getWriter().write(
                                "{\"message\":\"Password change required\"}"
                        );

                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (JwtException | IllegalArgumentException |
                 UsernameNotFoundException e) {

            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
