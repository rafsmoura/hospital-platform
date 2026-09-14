package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.scheduling.user.UserAccount;
import com.fiap.hospital.scheduling.user.UserRepository;
import com.fiap.hospital.scheduling.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AppointmentRelationshipValidator {

    private final UserRepository users;

    public AppointmentRelationshipValidator(UserRepository users) {
        this.users = users;
    }

    public void validate(UUID patientId, UUID doctorId) {
        UserAccount patient = users.findById(patientId)
                .orElseThrow(() -> new InvalidAppointmentRelationshipException("Patient does not exist"));
        if (patient.getRole() != UserRole.PACIENTE) {
            throw new InvalidAppointmentRelationshipException("Appointment patient must have PACIENTE role");
        }
        if (doctorId == null) {
            return;
        }
        UserAccount doctor = users.findById(doctorId)
                .orElseThrow(() -> new InvalidAppointmentRelationshipException("Doctor does not exist"));
        if (doctor.getRole() != UserRole.MEDICO) {
            throw new InvalidAppointmentRelationshipException("Appointment doctor must have MEDICO role");
        }
    }
}
