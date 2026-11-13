package lk.swiftdrop.user.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.swiftdrop.user.domain.model.ProfileStatus;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.infrastructure.crypto.EncryptedStringConverter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Maps {@code user_profile} and its {@code user_role} rows, created by {@code V2__create_user_profile_and_roles.sql}. */
@Entity
@Table(name = "user_profile")
class UserProfileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Plain E.164 number in memory, AES-GCM ciphertext in the column. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc", length = 512)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProfileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    protected UserProfileEntity() {
        // for JPA
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void apply(String newEmail, String newFullName, String newPhone, Set<UserRole> newRoles) {
        this.email = newEmail;
        this.fullName = newFullName;
        this.phone = newPhone;
        // Replace in place so Hibernate only inserts and deletes the rows that changed.
        this.roles.retainAll(newRoles);
        this.roles.addAll(newRoles);
    }

    String getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    String getPhone() {
        return phone;
    }

    ProfileStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Set<UserRole> getRoles() {
        return roles;
    }
}
