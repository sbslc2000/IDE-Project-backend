package goorm.dbjj.ide.auth.jwt;

import goorm.dbjj.ide.api.exception.BaseException;
import goorm.dbjj.ide.domain.user.UserRepository;
import goorm.dbjj.ide.domain.user.dto.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtIssuer {
    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${jwt.access.expiration}")
    private Long accessTokenExpirationPeriod;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenExpirationPeriod;

    private static final String KEY_ROLES = "role";

    private final UserRepository userRepository;

    // Base64 인코딩
    @PostConstruct
    void init(){
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    // todo: refresh 토큰이 들어왔을 때, accessToken만 생성.
    public String createToken(String refreshToken){
        Optional<User> user = userRepository.findByRefreshToken(refreshToken);
        User realUser = user.orElseThrow();

        Claims claims = Jwts.claims().setSubject(realUser.getEmail());
        claims.put(KEY_ROLES, "ROLE_USER");

        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(now.getTime() + accessTokenExpirationPeriod))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public TokenInfo createToken(String email, String role){

        Claims claims = Jwts.claims().setSubject(email);
        claims.put(KEY_ROLES, role);

        Date now = new Date();
        String accessToken = Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(now.getTime() + accessTokenExpirationPeriod))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();

        claims.setSubject(email);

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(now.getTime()+refreshTokenExpirationPeriod))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();

        return TokenInfo.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public String getSubject(Claims claims){
        log.trace("이메일 : {}", claims.getSubject());
        return claims.getSubject();
    }

    public Claims getClaims(String token){
        Claims claims;
        try{
            claims =  Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) { // 만료된 토큰
            claims = e.getClaims();
        } catch (Exception e) {
            throw new BaseException("유효한 토큰이 아닙니다.");
        }
        return claims;
    }
}