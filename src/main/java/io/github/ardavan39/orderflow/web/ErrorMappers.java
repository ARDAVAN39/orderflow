package io.github.ardavan39.orderflow.web;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.github.ardavan39.orderflow.order.InvalidOrderException;
import io.github.ardavan39.orderflow.order.OrderNotCancellableException;
import io.github.ardavan39.orderflow.order.OrderNotFoundException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response.Status;

/**
 * Turns domain exceptions into JSON error responses. Bean Validation failures are handled by
 * Quarkus and come back as 400 with a list of violations.
 */
public class ErrorMappers {

    public record ApiError(int status, String error, String detail) {

        static RestResponse<ApiError> of(Status status, String detail) {
            return RestResponse.status(status, new ApiError(status.getStatusCode(), status.getReasonPhrase(), detail));
        }
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> invalidOrder(InvalidOrderException e) {
        return ApiError.of(Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> orderNotFound(OrderNotFoundException e) {
        return ApiError.of(Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> notFound(NotFoundException e) {
        return ApiError.of(Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> notCancellable(OrderNotCancellableException e) {
        return ApiError.of(Status.CONFLICT, e.getMessage());
    }
}
