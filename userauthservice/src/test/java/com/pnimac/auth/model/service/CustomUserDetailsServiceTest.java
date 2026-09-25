package com.pnimac.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import javax.management.relation.RoleNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.pnimac.auth.model.Role;
import com.pnimac.auth.model.User;
import com.pnimac.auth.model.repository.RoleRepository;
import com.pnimac.auth.model.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository, roleRepository);
    }

    /**
     * Verifies that a database role such as USER becomes the Spring Security
     * authority ROLE_USER. This protects role-based authorization from a naming
     * mismatch between persisted roles and Spring Security.
     */
    @Test
    void loadsRolesWithSpringPrefix() {
        Role role = new Role();
        role.setRolename("USER");
        User user = new User();
        user.setUsername("alice");
        user.setPassword("hash");
        user.setRoles(Set.of(role));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("alice").getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    /**
     * Verifies that looking up an unknown username fails with the exception expected
     * by Spring Security instead of returning null or an incomplete principal.
     */
    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    /**
     * Verifies that a new account is enabled, receives the default USER role, and is
     * flushed to the repository. This protects the signup invariant for usable,
     * minimally privileged accounts.
     */
    @Test
    void savesWithDefaultRole() throws Exception {
        Role role = new Role();
        role.setRolename("USER");
        User user = new User();
        user.setUsername("alice");
        when(roleRepository.findByRolename("USER")).thenReturn(Optional.of(role));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        assertThat(service.save(user).getRoles()).containsExactly(role);
        assertThat(user.isEnabled()).isTrue();
        verify(userRepository).saveAndFlush(user);
    }

    /**
     * Verifies that account creation fails explicitly when the required default USER
     * role is missing. This prevents persisting accounts with no usable authority.
     */
    @Test
    void failsWhenDefaultRoleIsMissing() {
        User user = new User();
        user.setUsername("alice");
        when(roleRepository.findByRolename("USER")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(user)).isInstanceOf(RoleNotFoundException.class);
    }
}
