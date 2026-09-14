package com.fiap.hospital.scheduling.security;

import com.fiap.hospital.scheduling.user.UserRepository;
import com.fiap.hospital.scheduling.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void protectedEndpointRequiresBasicAuthentication() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededUsersAuthenticateWithTheirRoles() throws Exception {
        Map<String, UserRole> expected = Map.of(
                "admin", UserRole.ADMIN,
                "medico", UserRole.MEDICO,
                "enfermeiro", UserRole.ENFERMEIRO,
                "paciente", UserRole.PACIENTE);

        expected.forEach((username, role) -> {
            var account = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
            assertThat(account.getRole()).isEqualTo(role);
            assertThat(account.getPasswordHash()).isNotEqualTo("password");
            assertThat(passwordEncoder.matches("password", account.getPasswordHash())).isTrue();
            try {
                mockMvc.perform(get("/test/protected").with(httpBasic(username, "password")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value(role.name()));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @TestConfiguration
    static class TestEndpointConfiguration {
        @Bean
        ProtectedEndpoint protectedEndpoint() {
            return new ProtectedEndpoint();
        }
    }

    @RestController
    static class ProtectedEndpoint {
        @GetMapping("/test/protected")
        Map<String, String> protectedResource(Authentication authentication) {
            String role = authentication.getAuthorities().iterator().next().getAuthority().substring(5);
            return Map.of("role", role);
        }
    }
}
