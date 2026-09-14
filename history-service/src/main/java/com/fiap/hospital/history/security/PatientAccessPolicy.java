package com.fiap.hospital.history.security;

import com.fiap.hospital.history.user.UserAccount;
import com.fiap.hospital.history.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PatientAccessPolicy {

    private final UserRepository users;

    public PatientAccessPolicy(UserRepository users) {
        this.users = users;
    }

    public void assertCanAccessPatient(UUID requestedPatientId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "MEDICO") || hasRole(authentication, "ENFERMEIRO")) {
            return;
        }
        if (!hasRole(authentication, "PACIENTE")) {
            throw new AccessDeniedException("Clinical history access denied");
        }

        UUID authenticatedPatientId = users.findByUsernameIgnoreCase(authentication.getName())
                .map(UserAccount::getId)
                .orElseThrow(() -> new AccessDeniedException("Patient identity not found"));
        if (!authenticatedPatientId.equals(requestedPatientId)) {
            throw new AccessDeniedException("Patient history ownership denied");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
