package games.noriter.api.user;

import games.noriter.api.user.domain.AppUser;
import games.noriter.api.user.infra.AppUserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository users;

    @Transactional
    public UserSummary findOrCreate(String provider, String providerId, String nickname) {
        return users.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> users.save(new AppUser(provider, providerId, nickname)))
                .toSummary();
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(Long id) {
        return users.findById(id).map(AppUser::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserSummary> findSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.findAllByIdIn(ids).stream()
                .map(AppUser::toSummary)
                .collect(Collectors.toMap(UserSummary::id, s -> s));
    }
}
