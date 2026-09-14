package com.fiap.hospital.scheduling.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserCreationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserCreationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        UserRole role = parseRole(request.role());
        if (role == UserRole.ADMIN) {
            throw new IllegalArgumentException("ADMIN users are seeded and cannot be created");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUsernameException(username);
        }

        UserAccount user = new UserAccount(
                UUID.randomUUID(), username, passwordEncoder.encode(request.password()), role, true);
        return UserResponse.from(users.save(user));
    }

    private UserRole parseRole(String role) {
        try {
            return UserRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported user role");
        }
    }
}
