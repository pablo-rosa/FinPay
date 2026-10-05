package com.finpay.users.application;

import com.finpay.users.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${finpay.security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl
    ) {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token lifetime must be positive");
        }
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
    }

    public IssuedToken issue(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        UUID tokenId = UUID.randomUUID();
        var claims = JwtClaimsSet.builder()
                .issuer("finpay")
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId.toString())
                .claim("email", user.email())
                .claim("roles", user.roles().stream().map(Enum::name).sorted().toList())
                .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new IssuedToken(token, tokenId, expiresAt);
    }

    public record IssuedToken(String value, UUID tokenId, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[value=[REDACTED], tokenId=" + tokenId + ", expiresAt=" + expiresAt + "]";
        }
    }
}
