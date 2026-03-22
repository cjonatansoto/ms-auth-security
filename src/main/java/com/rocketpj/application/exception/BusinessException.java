package com.rocketpj.application.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Getter
@JsonIgnoreProperties({"cause", "stackTrace", "localizedMessage", "suppressed"})
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final EnumError errorEnum;
    private final Throwable cause;

    private BusinessException(Builder builder) {
        super(builder.messageKey, builder.cause);
        this.status = builder.status;
        this.code = builder.code;
        this.messageKey = builder.messageKey;
        this.errorEnum = builder.errorEnum;
        this.cause = builder.cause;
    }

    public static Builder from(EnumError enumError) {
        return new Builder(enumError);
    }

    @Override
    public synchronized Throwable getCause() {
        return cause;
    }

    public Optional<Throwable> getOptionalCause() {
        return Optional.ofNullable(cause);
    }

    public static class Builder {
        private final EnumError errorEnum;
        private HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        private Throwable cause;
        private String code;
        private String messageKey;

        private Builder(EnumError errorEnum) {
            this.errorEnum = errorEnum;
            this.code = errorEnum.getCode();
            this.messageKey = errorEnum.getMessageKey();
        }

        public Builder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder badRequest() {
            return status(HttpStatus.BAD_REQUEST);
        }

        public Builder unauthorized() {
            return status(HttpStatus.UNAUTHORIZED);
        }

        public Builder forbidden() {
            return status(HttpStatus.FORBIDDEN);
        }

        public Builder notFound() {
            return status(HttpStatus.NOT_FOUND);
        }

        public Builder methodNotAllowed() {
            return status(HttpStatus.METHOD_NOT_ALLOWED);
        }

        public Builder notAcceptable() {
            return status(HttpStatus.NOT_ACCEPTABLE);
        }

        public Builder conflict() {
            return status(HttpStatus.CONFLICT);
        }

        public Builder gone() {
            return status(HttpStatus.GONE);
        }

        public Builder unsupportedMediaType() {
            return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        public Builder unprocessableEntity() {
            return status(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        public Builder tooManyRequests() {
            return status(HttpStatus.TOO_MANY_REQUESTS);
        }

        public Builder internalServerError() {
            return status(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        public Builder notImplemented() {
            return status(HttpStatus.NOT_IMPLEMENTED);
        }

        public Builder badGateway() {
            return status(HttpStatus.BAD_GATEWAY);
        }

        public Builder serviceUnavailable() {
            return status(HttpStatus.SERVICE_UNAVAILABLE);
        }

        public Builder gatewayTimeout() {
            return status(HttpStatus.GATEWAY_TIMEOUT);
        }

        public Builder httpVersionNotSupported() {
            return status(HttpStatus.HTTP_VERSION_NOT_SUPPORTED);
        }

        public BusinessException build() {
            return new BusinessException(this);
        }

    }
}
