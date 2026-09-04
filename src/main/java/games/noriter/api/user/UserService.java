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

    /** 소셜 로그인 콜백에서 호출. 없으면 생성한다. */
    @Transactional
    public UserSummary findOrCreate(String provider, String providerId, String nickname) {
        return users.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> users.save(new AppUser(provider, providerId, nickname)))
                .toSummary();
    }

    public Optional<UserSummary> findById(Long id) {
        return users.findById(id).map(AppUser::toSummary);
    }

    /** 리더보드 등에서 여러 유저의 닉네임을 한 번에 조회. */
    public Map<Long, UserSummary> findSummaries(Collection<Long> ids) {
        return users.findAllByIdIn(ids).stream()
                .map(AppUser::toSummary)
                .collect(Collectors.toMap(UserSummary::id, s -> s));
    }
}
