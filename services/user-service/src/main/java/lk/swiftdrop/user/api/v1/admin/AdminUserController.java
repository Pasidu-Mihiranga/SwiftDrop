package lk.swiftdrop.user.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.user.api.dto.AdminUserPageResponse;
import lk.swiftdrop.user.api.dto.AdminUserResponse;
import lk.swiftdrop.user.api.dto.CreateStaffRequest;
import lk.swiftdrop.user.api.dto.CreateStaffResponse;
import lk.swiftdrop.user.api.dto.UpdateUserRequest;
import lk.swiftdrop.user.api.mapper.AdminUserApiMapper;
import lk.swiftdrop.user.application.command.AdminUserService;
import lk.swiftdrop.user.application.query.AdminUserQueryService;
import lk.swiftdrop.user.domain.model.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Admin user management: search accounts, create staff accounts, enable or disable them and change staff roles. */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin users", description = "Account management (ADMIN)")
public class AdminUserController {

    private final AdminUserQueryService queries;
    private final AdminUserService commands;

    public AdminUserController(AdminUserQueryService queries, AdminUserService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    @Operation(summary = "Search accounts by name or e-mail, optionally by role and enabled state (ADMIN)")
    public AdminUserPageResponse search(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) UserRole role,
                                        @RequestParam(required = false) Boolean enabled,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return AdminUserApiMapper.toResponse(queries.search(search, role, enabled, page, size));
    }

    @PostMapping
    @Operation(summary = "Create a courier, dispatcher or admin account; Keycloak e-mails a set-password link (ADMIN)")
    public ResponseEntity<CreateStaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        AdminUserService.CreatedAccount created = commands.create(AdminUserApiMapper.toAccount(request));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.user().id()))
                .body(new CreateStaffResponse(AdminUserApiMapper.toResponse(created.user()), created.invitationSent()));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Enable or disable an account and/or replace its staff roles (ADMIN)")
    public AdminUserResponse update(AuthenticatedUser caller, @PathVariable String userId,
                                    @RequestBody UpdateUserRequest request) {
        return AdminUserApiMapper.toResponse(commands.update(caller.id(), userId, request.enabled(), request.roles()));
    }

    @PostMapping("/{userId}/invitation")
    @Operation(summary = "Send the set-password e-mail again (ADMIN)")
    public ResponseEntity<Void> resendInvitation(@PathVariable String userId) {
        commands.resendInvitation(userId);
        return ResponseEntity.accepted().build();
    }
}
