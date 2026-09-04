package games.noriter.api.user;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;

    UserService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserSummary findOrCreate(String provider, String providerId, String nickname) {
        return users.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> users.save(new AppUser(provider, providerId, nickname)))
                .toSummary();
    }

    public Optional<UserSummary> findById(Long id) {
        return users.findById(id).map(AppUser::toSummary);
    }

    public Map<Long, UserSummary> findSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.findAllByIdIn(ids).stream()
                .map(AppUser::toSummary)
                .collect(Collectors.toMap(UserSummary::id, s -> s));
    }
}
