package com.fiap.hospital.scheduling.user;

import java.util.UUID;

public record UserResponse(UUID id, String username, UserRole role, boolean active) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isActive());
    }
}
