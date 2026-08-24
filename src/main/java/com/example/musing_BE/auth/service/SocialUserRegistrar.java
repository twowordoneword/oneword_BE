package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 소셜 사용자 조회/등록.
 *
 * <p>{@code AuthService}에서 분리한 이유는 <b>트랜잭션 경계</b> 때문이다. 최초 로그인이 동시에
 * 두 번 들어오면 늦은 쪽이 {@code uq_users_provider}에 걸리고, 그 순간 트랜잭션은
 * rollback-only가 되어 같은 트랜잭션 안에서는 재조회조차 할 수 없다.
 * 이 클래스가 별도 트랜잭션을 가지므로 호출부가 실패한 트랜잭션을 버리고 새로 조회할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SocialUserRegistrar {

    private final UserRepository userRepository;

    @Transactional
    public Result findOrCreate(SocialProvider provider, SocialUserInfo socialUser, String requestedNickname) {
        Optional<User> existing =
                userRepository.findByProviderAndProviderId(provider.code(), socialUser.providerId());
        if (existing.isPresent()) {
            User user = existing.get();
            user.updateNickname(requestedNickname);
            return new Result(user, false);
        }

        // saveAndFlush로 INSERT를 지금 보낸다. 커밋까지 미루면 제약 위반이 호출부가 아니라
        // 트랜잭션 커밋 시점에 터져 어디서 실패했는지 잡기 어려워진다.
        User created = userRepository.saveAndFlush(User.create(
                resolveEmail(provider, socialUser),
                resolveNickname(provider, socialUser, requestedNickname),
                provider.code(),
                socialUser.providerId()
        ));
        return new Result(created, true);
    }

    /** 이메일 제공에 동의하지 않은 계정도 있다. 그 경우 충돌하지 않는 대체 주소를 만든다. */
    private String resolveEmail(SocialProvider provider, SocialUserInfo socialUser) {
        String email = socialUser.email();
        if (email == null || email.isBlank()) {
            return provider.code() + "_" + socialUser.providerId() + "@social.local";
        }
        return email;
    }

    private String resolveNickname(SocialProvider provider, SocialUserInfo socialUser, String requestedNickname) {
        if (requestedNickname != null && !requestedNickname.isBlank()) {
            return requestedNickname;
        }
        if (socialUser.nickname() != null && !socialUser.nickname().isBlank()) {
            return socialUser.nickname();
        }
        return provider.code() + "_" + socialUser.providerId();
    }

    public record Result(User user, boolean isNewUser) {}
}
