package com.finpay.users.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class CurrentUserController {

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                Set.copyOf(jwt.getClaimAsStringList("roles"))
        );
    }
}
