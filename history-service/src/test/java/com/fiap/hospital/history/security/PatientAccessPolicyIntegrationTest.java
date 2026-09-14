package com.fiap.hospital.history.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientAccessPolicyIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void historyOperationRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/test/patients/" + PATIENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffMayAccessRequestedPatientAndPatientMayAccessOwnHistory() throws Exception {
        mockMvc.perform(get("/test/patients/" + PATIENT_ID).with(httpBasic("medico", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()));

        mockMvc.perform(get("/test/patients/" + PATIENT_ID).with(httpBasic("paciente", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()));
    }

    @Test
    void patientIdTamperingIsRejectedWithoutReturningRequestedData() throws Exception {
        mockMvc.perform(get("/test/patients/" + OTHER_PATIENT_ID).with(httpBasic("paciente", "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(OTHER_PATIENT_ID.toString()))));
    }

    @Test
    void nonPatientRoleCannotUsePatientOnlyOperation() throws Exception {
        mockMvc.perform(get("/test/patients/" + PATIENT_ID).with(httpBasic("admin", "password")))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class TestEndpointConfiguration {
        @Bean
        ProtectedPatientEndpoint protectedPatientEndpoint(PatientAccessPolicy policy) {
            return new ProtectedPatientEndpoint(policy);
        }
    }

    @RestController
    static class ProtectedPatientEndpoint {
        private final PatientAccessPolicy policy;

        ProtectedPatientEndpoint(PatientAccessPolicy policy) {
            this.policy = policy;
        }

        @GetMapping("/test/patients/{patientId}")
        Map<String, String> getPatient(@PathVariable("patientId") UUID patientId, Authentication authentication) {
            policy.assertCanAccessPatient(patientId, authentication);
            return Map.of("patientId", patientId.toString());
        }
    }
}
