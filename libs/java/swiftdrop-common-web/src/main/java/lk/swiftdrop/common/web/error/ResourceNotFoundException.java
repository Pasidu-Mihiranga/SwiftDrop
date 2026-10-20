package lk.swiftdrop.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * 404. Also used when a caller asks for a resource it does not own, so the API never reveals
 * that someone else's parcel exists.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        String code = resource.toUpperCase().replace(' ', '_') + "_NOT_FOUND";
        return new ResourceNotFoundException(code, resource + " " + id + " was not found");
    }
}
