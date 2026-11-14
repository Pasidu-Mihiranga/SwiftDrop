package lk.swiftdrop.user.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.user.api.dto.AddressRequest;
import lk.swiftdrop.user.api.dto.AddressResponse;
import lk.swiftdrop.user.api.mapper.AddressApiMapper;
import lk.swiftdrop.user.api.mapper.ProfileApiMapper;
import lk.swiftdrop.user.application.command.AddressBookService;
import lk.swiftdrop.user.domain.model.Address;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The caller's own address book. The owner is always the token's subject, never a value from the request. */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN')")
@Tag(name = "Address book", description = "The caller's saved addresses")
public class AddressController {

    private final AddressBookService addressBook;

    public AddressController(AddressBookService addressBook) {
        this.addressBook = addressBook;
    }

    @GetMapping
    @Operation(summary = "The caller's saved addresses, default first")
    public List<AddressResponse> list(AuthenticatedUser user) {
        return addressBook.list(user.id()).stream().map(AddressApiMapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Save an address (the first one becomes the default)")
    public ResponseEntity<AddressResponse> add(AuthenticatedUser user, @Valid @RequestBody AddressRequest request) {
        Address saved = addressBook.add(ProfileApiMapper.toClaims(user), AddressApiMapper.toDetails(request), request.makeDefault());
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + saved.id()))
                .body(AddressApiMapper.toResponse(saved));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Replace a saved address; isDefault=true makes it the default")
    public AddressResponse update(AuthenticatedUser user, @PathVariable UUID addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return AddressApiMapper.toResponse(
                addressBook.update(user.id(), addressId, AddressApiMapper.toDetails(request), request.makeDefault()));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete a saved address; deleting the default promotes the oldest remaining one")
    public ResponseEntity<Void> delete(AuthenticatedUser user, @PathVariable UUID addressId) {
        addressBook.delete(user.id(), addressId);
        return ResponseEntity.noContent().build();
    }
}
