package com.mtriet.tamlottery.identity.application;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.api.AuthDtos.TokenResponse;
import com.mtriet.tamlottery.identity.config.JwtProperties;
import com.mtriet.tamlottery.identity.domain.RefreshToken;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.domain.UserStatus;
import com.mtriet.tamlottery.identity.infrastructure.RefreshTokenRepository;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse login(String username, String password) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(AuthService::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issue(user, Instant.now());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (!existing.isUsable(now) || existing.getUser().getStatus() != UserStatus.ACTIVE) {
            throw invalidRefreshToken();
        }

        RawRefresh replacement = newRawRefresh();
        existing.revoke(now, replacement.hash());
        refreshTokenRepository.save(new RefreshToken(
                existing.getUser(), replacement.hash(), now.plus(properties.refreshTokenTtl())));
        return new TokenResponse(
                "Bearer",
                jwtService.createAccessToken(existing.getUser(), now),
                replacement.raw(),
                properties.accessTokenTtl().toSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(Instant.now(), null));
    }

    private TokenResponse issue(UserAccount user, Instant now) {
        RawRefresh refresh = newRawRefresh();
        refreshTokenRepository.save(new RefreshToken(user, refresh.hash(), now.plus(properties.refreshTokenTtl())));
        return new TokenResponse(
                "Bearer",
                jwtService.createAccessToken(user, now),
                refresh.raw(),
                properties.accessTokenTtl().toSeconds());
    }

    private RawRefresh newRawRefresh() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RawRefresh(raw, hash(raw));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
    }

    private record RawRefresh(String raw, String hash) {
    }
}

