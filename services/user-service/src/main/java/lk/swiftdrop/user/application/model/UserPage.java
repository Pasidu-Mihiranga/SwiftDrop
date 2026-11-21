package lk.swiftdrop.user.application.model;

import java.util.List;

/**
 * One page of a user search.
 *
 * @param page zero-based page number
 */
public record UserPage(List<ManagedUser> items, long total, int page, int size) {

    public UserPage {
        items = List.copyOf(items);
    }
}
