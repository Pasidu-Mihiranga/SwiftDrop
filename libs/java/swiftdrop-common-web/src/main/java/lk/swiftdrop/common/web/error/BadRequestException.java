package lk.swiftdrop.common.web.error;

import org.springframework.http.HttpStatus;

/** 400 for input problems that bean validation cannot express. */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
