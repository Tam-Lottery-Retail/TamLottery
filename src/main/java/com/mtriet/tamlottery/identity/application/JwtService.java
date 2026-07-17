package com.mtriet.tamlottery.identity.application;

import com.mtriet.tamlottery.identity.config.JwtProperties;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.infrastructure.SellerRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final SellerRepository sellerRepository;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties, SellerRepository sellerRepository) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.sellerRepository = sellerRepository;
    }

    public String createAccessToken(UserAccount user, Instant now) {
        List<String> roles = user.getRoles().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Role::name)
                .toList();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("storeId", user.getStore().getId())
                .claim("roles", roles);
        sellerRepository.findByUserId(user.getId()).ifPresent(seller -> claims.claim("sellerId", seller.getId()));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}

