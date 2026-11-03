package com.mathweb.security;

import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void testLoadUserByUsername_Success_Student() {
        User user = createUser("student@example.com", Role.STUDENT, true, true);

        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("student@example.com");

        assertNotNull(userDetails);
        assertEquals("student@example.com", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT")));
    }

    @Test
    void testLoadUserByUsername_Success_Teacher() {
        User user = createUser("teacher@example.com", Role.TEACHER, true, true);

        when(userRepository.findByEmail("teacher@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("teacher@example.com");

        assertEquals("teacher@example.com", userDetails.getUsername());

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER")));
    }

    @Test
    void testLoadUserByUsername_Success_Admin() {
        User user = createUser("admin@example.com", Role.ADMIN, true, true);

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("admin@example.com");

        assertEquals("admin@example.com", userDetails.getUsername());

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testLoadUserByUsername_UserNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("nonexistent@example.com")
        );

        assertEquals(
                "User not found with email: nonexistent@example.com",
                exception.getMessage()
        );
    }

    @Test
    void testLoadUserById_Success() {
        User user = createUser("test@example.com", Role.STUDENT, true, true);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserById(1L);

        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    void testLoadUserById_UserNotFound() {
        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserById(999L)
        );

        assertEquals("User not found with id: 999", exception.getMessage());
    }

    @Test
    void testLoadUserByUsername_InactiveUser() {
        User user = createUser("inactive@example.com", Role.STUDENT, true, false);

        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("inactive@example.com");

        assertFalse(userDetails.isEnabled());
        assertFalse(userDetails.isAccountNonLocked());
    }

    @Test
    void testLoadUserByUsername_EmailNotVerified() {
        User user = createUser("unverified@example.com", Role.STUDENT, false, true);

        when(userRepository.findByEmail("unverified@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("unverified@example.com");

        assertFalse(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
    }

    private User createUser(String email, Role role,
                            boolean emailVerified, boolean active) {
        User user = User.builder()
                .email(email)
                .firstName("John")
                .lastName("Doe")
                .passwordHash("encodedPassword")
                .role(role)
                .emailVerified(emailVerified)
                .active(active)
                .build();

        user.setId(1L);
        return user;
    }
}