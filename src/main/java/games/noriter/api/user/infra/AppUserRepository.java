package games.noriter.api.user.infra;

import games.noriter.api.user.domain.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
    List<AppUser> findAllByIdIn(Collection<Long> ids);
}
