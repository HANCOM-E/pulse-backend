package com.hancome.pulse.auth;

import com.hancome.pulse.auth.dto.LoginRequest;
import com.hancome.pulse.auth.dto.SignupRequest;
import com.hancome.pulse.auth.dto.SignupResponse;
import com.hancome.pulse.auth.dto.TokenResponse;
import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // 로그인 시 "유저 없음"과 "비번 틀림"의 처리 시간을 맞추기 위한 더미 해시.
    // 유저가 없을 때도 BCrypt 검증을 한 번 돌려, 응답 시간차로 이메일 존재 여부가 새지 않게 한다(CWE-208).
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public SignupResponse signUp(SignupRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user;
        try {
            user = userRepository.save(new User(req.email(), passwordEncoder.encode(req.password())));
        } catch (DataIntegrityViolationException e) {
            // 위 검사와 저장 사이의 동시 가입 레이스 → DB unique 제약 위반을 같은 충돌로 매핑
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // 가입과 동시에 토큰 발급(자동 로그인) — 팀 API 명세서 확정.
        String token = jwtProvider.generateToken(user.getId());
        return new SignupResponse(
                user.getId(), user.getEmail(), user.getCreatedAt(), token, jwtProvider.getExpirationInSeconds());
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email()).orElse(null);

        // 유저가 없어도 더미 해시로 검증을 돌려 처리 시간을 맞춘다(이메일 존재 여부 유추 차단).
        // 두 실패 경로 모두 같은 일반화 메시지로 통일해 어느 쪽이 틀렸는지 노출하지 않는다.
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.generateToken(user.getId());
        return new TokenResponse(token, jwtProvider.getExpirationInSeconds());
    }
}
