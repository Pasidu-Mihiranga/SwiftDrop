package lk.swiftdrop.common.web.error;

import org.springframework.http.HttpStatus;

/** 409: the request conflicts with current state (duplicate registration, stale version, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
