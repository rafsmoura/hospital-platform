package com.fiap.hospital.history.graphql;

import com.fiap.hospital.history.projection.HistoryAppointment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
public class HistoryGraphQlController {

    private final HistoryQueryService queries;

    public HistoryGraphQlController(HistoryQueryService queries) {
        this.queries = queries;
    }

    @QueryMapping
    public List<AppointmentView> patientHistory(@Argument("patientId") String patientId,
                                                @Argument("futureOnly") boolean futureOnly,
                                                @Argument("from") String from,
                                                @Argument("sort") AppointmentSort sort) {
        Instant fromInstant = from == null ? null : Instant.parse(from);
        return queries.findHistory(UUID.fromString(patientId), futureOnly, fromInstant,
                        sort == null ? AppointmentSort.ASC : sort, authentication())
                .stream().map(AppointmentView::from).toList();
    }

    @QueryMapping
    public AppointmentView appointment(@Argument("id") String id) {
        try {
            HistoryAppointment appointment = queries.findAppointment(UUID.fromString(id), authentication());
            return appointment == null ? null : AppointmentView.from(appointment);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
