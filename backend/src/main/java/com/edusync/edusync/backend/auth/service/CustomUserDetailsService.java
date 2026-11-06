package com.edusync.edusync.backend.auth.service;

import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.entity.UserRole;
import com.edusync.edusync.backend.users.repository.UserRepository;
import com.edusync.edusync.backend.users.repository.UserRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        List<SimpleGrantedAuthority> authorities =
                userRoleRepository.findByUserId(user.getUserId())
                        .stream()
                        .map(UserRole::getRole)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!"ACTIVE".equals(user.getStatus()))
                .build();
    }
}