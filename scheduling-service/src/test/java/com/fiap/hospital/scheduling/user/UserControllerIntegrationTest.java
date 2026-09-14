package com.fiap.hospital.scheduling.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCreatesUserAndResponseRedactsCredentials() throws Exception {
        String request = "{\"username\":\"new-doctor\",\"password\":\"strong-pass\",\"role\":\"MEDICO\"}";

        mockMvc.perform(post("/api/v1/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.username").value("new-doctor"))
                .andExpect(jsonPath("$.role").value("MEDICO"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());

        UserAccount stored = userRepository.findByUsernameIgnoreCase("new-doctor").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("strong-pass");
        assertThat(passwordEncoder.matches("strong-pass", stored.getPasswordHash())).isTrue();
    }

    @Test
    void unauthenticatedUserCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("unauthenticated")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"medico", "enfermeiro", "paciente"})
    void nonAdminRolesReceiveForbidden(String username) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(httpBasic(username, "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("forbidden-" + username)))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedRequestDoesNotPersistAUser() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(post("/api/v1/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"short\",\"role\":\"MEDICO\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidRoleDoesNotPersistAUser() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(post("/api/v1/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"invalid-role\",\"password\":\"strong-pass\",\"role\":\"VISITANTE\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsernameIgnoreCase("invalid-role")).isEmpty();
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    void duplicateUsernameReturnsConflictWithoutReplacingStoredUser() throws Exception {
        String request = validRequest("admin");

        mockMvc.perform(post("/api/v1/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        UserAccount admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    private String validRequest(String username) throws Exception {
        return objectMapper.writeValueAsString(new CreateUserRequest(username, "strong-pass", "PACIENTE"));
    }
}
