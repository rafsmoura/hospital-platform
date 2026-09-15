package com.fiap.hospital.history.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class HistoryGraphQlExceptionResolver implements DataFetcherExceptionResolver {

    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
        if (!(exception instanceof InvalidRequestParametersException)) {
            return Mono.empty();
        }

        return Mono.just(List.of(GraphqlErrorBuilder.newError(environment)
                .message("Invalid request parameters")
                .errorType(ErrorType.BAD_REQUEST)
                .build()));
    }
}
