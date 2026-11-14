package lk.swiftdrop.user.infrastructure.persistence;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class UserProfileRepositoryAdapter implements UserProfileRepository {

    private final UserProfileJpaRepository profiles;

    UserProfileRepositoryAdapter(UserProfileJpaRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<UserProfile> findById(String id) {
        return profiles.findById(id).map(UserProfileRepositoryAdapter::toDomain);
    }

    @Override
    public boolean createIfAbsent(UserProfile profile) {
        int inserted;
        try {
            inserted = profiles.insertIfAbsent(profile.id(), profile.email(), profile.fullName());
        } catch (DataIntegrityViolationException e) {
            // The id is new but the e-mail belongs to another profile, e.g. a Keycloak user that was
            // deleted and created again. An admin has to resolve this; never merge two people.
            throw emailAlreadyLinked();
        }
        if (inserted == 0) {
            return false;
        }
        for (UserRole role : profile.roles()) {
            profiles.insertRoleIfAbsent(profile.id(), role.name());
        }
        return true;
    }

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileEntity entity = profiles.findById(profile.id()).orElseThrow(
                () -> new IllegalStateException("Profile " + profile.id() + " disappeared while being saved"));
        entity.apply(profile.email(), profile.fullName(),
                profile.phoneNumber().map(PhoneNumber::value).orElse(null), profile.roles());
        try {
            return toDomain(profiles.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw emailAlreadyLinked();
        }
    }

    private static ConflictException emailAlreadyLinked() {
        return new ConflictException("EMAIL_ALREADY_LINKED",
                "This e-mail address is already linked to another SwiftDrop profile");
    }

    private static UserProfile toDomain(UserProfileEntity entity) {
        return new UserProfile(entity.getId(), entity.getEmail(), entity.getFullName(),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()),
                entity.getStatus(), entity.getRoles(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
