package com.bestpriceengine.ingestion.security;

import com.bestpriceengine.ingestion.domain.User;
import com.bestpriceengine.ingestion.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the full {@link User} row behind an authenticated request's principal. */
@Component
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolve(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "authenticated principal has no matching User row: " + authentication.getName()));
    }
}
