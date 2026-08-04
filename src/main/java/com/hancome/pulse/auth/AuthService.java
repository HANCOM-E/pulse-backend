package com.hancome.pulse.auth;

import com.hancome.pulse.auth.dto.LoginRequest;
import com.hancome.pulse.auth.dto.SignupRequest;
import com.hancome.pulse.auth.dto.TokenResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public void signUp(SignupRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalStateException("이미 가입된 이메일");
        } else {
            String hash = passwordEncoder.encode(req.password());
            userRepository.save(new User(req.email(), hash));
        }
    }
    ;

    public TokenResponse login(LoginRequest req) {
        User user =
                userRepository.findByEmail(req.email()).orElseThrow(() -> new BadCredentialsException("존재하지 않는 사용자"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadCredentialsException("비밀번호 불일치");

        String token = jwtProvider.generateToken(user.getId());

        return new TokenResponse(token, jwtProvider.getExpirationInSeconds());
    }
    ;
}
