package lk.swiftdrop.common.web.error;

import org.springframework.http.HttpStatus;

/** 422: the request is well-formed but breaks a domain rule (e.g. invalid status transition). */
public class BusinessRuleViolationException extends ApiException {

    public BusinessRuleViolationException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
