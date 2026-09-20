package kr.or.sportmap.member.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** 애플리케이션 기능을 구성한다. */
@Configuration
public class SecurityConfig {

  private static final String ISSUER = "sportmap";
  private static final String COOKIE = "ACCESS_TOKEN";

  @Bean
  SecretKey jwtSecret(
    @Value("${app.auth.jwt-secret:}") String configured,
    Environment environment
  ) {
    if (
      configured.isBlank() && !environment.acceptsProfiles(Profiles.of("local"))
    ) throw new IllegalStateException(
      "JWT_SECRET must be set outside the local profile"
    );
    byte[] bytes;
    if (configured.isBlank()) {
      bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
    } else {
      bytes = configured.getBytes(StandardCharsets.UTF_8);
      if (bytes.length < 32) throw new IllegalStateException(
        "JWT_SECRET must be at least 32 UTF-8 bytes"
      );
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey key) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
      .macAlgorithm(MacAlgorithm.HS256)
      .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    return decoder;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
    return request -> {
      // 재시작 전 JWT가 남아 있어도 공개 조회와 로그인·회원가입은 차단하지 않는다.
      String path = request.getRequestURI();
      if (
        (!path.startsWith("/api/") && !path.startsWith("/h2-console/")) ||
        path.equals("/api/csrf") ||
        path.equals("/api/users/login") ||
        path.equals("/api/users/signup") ||
        path.equals("/api/users/email/send") ||
        path.equals("/api/users/email/status") ||
        path.equals("/api/users/email/confirm") ||
        (request.getMethod().equals("GET") &&
          (path.startsWith("/api/facilities/") ||
            path.equals("/api/facilities") ||
            path.startsWith("/api/programs") ||
            path.startsWith("/api/regions") ||
            path.startsWith("/api/recommendations") ||
            path.equals("/api/fitness/prescriptions") ||
            path.equals("/api/fitness/centers") ||
            path.startsWith("/api/notices") ||
            path.startsWith("/api/qna")))
      ) return null;
      String token = header.resolve(request);
      if (token != null) return token;
      if (request.getCookies() == null) return null;
      for (Cookie cookie : request.getCookies())
        if (COOKIE.equals(cookie.getName())) return cookie.getValue();
      return null;
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    BearerTokenResolver tokenResolver,
    MemberRepository members
  ) throws Exception {
    http.sessionManagement(session ->
      session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );
    http.csrf(csrf ->
      csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        .ignoringRequestMatchers(
          "/api/import/facilities",
          "/api/import/programs"
        )
    );
    http.authorizeHttpRequests(auth ->
      auth
        .requestMatchers("/error")
        .permitAll()
        // React 화면과 빌드 자산만 공개한다. 보호된 /api 요청은 아래 규칙을 따른다.
        .requestMatchers(
          HttpMethod.GET,
          "/",
          "/index.html",
          "/assets/**",
          "/favicon.ico",
          "/favicon.svg",
          "/live-facilities",
          "/facilities/**",
          "/programs",
          "/programs/**",
          "/recommendations",
          "/fitness",
          "/fitness-centers",
          "/reservations",
          "/community",
          "/community/**",
          "/account",
          "/admin"
        )
        .permitAll()
        .requestMatchers(
          HttpMethod.GET,
          "/api/csrf",
          "/api/facilities/**",
          "/api/programs/**",
          "/api/regions/**",
          "/api/recommendations/**",
          "/api/fitness/prescriptions",
          "/api/fitness/centers",
          "/api/notices/**",
          "/api/qna/**"
        )
        .permitAll()
        .requestMatchers(
          HttpMethod.POST,
          "/api/users/signup",
          "/api/users/email/send",
          "/api/users/email/status",
          "/api/users/login",
          "/api/import/facilities",
          "/api/import/programs"
        )
        .permitAll()
        .requestMatchers(HttpMethod.GET, "/api/users/email/confirm")
        .permitAll()
        .requestMatchers("/api/admin/**")
        .access((authentication, request) -> {
          if (
            !(authentication.get() instanceof JwtAuthenticationToken token) ||
            token
              .getAuthorities()
              .stream()
              .noneMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
              )
          ) return new AuthorizationDecision(false);
          try {
            Long id = Long.valueOf(token.getToken().getSubject());
            return new AuthorizationDecision(
              members
                .findById(id)
                .map(member -> member.getRole() == Member.Role.ADMIN)
                .orElse(false)
            );
          } catch (NumberFormatException error) {
            return new AuthorizationDecision(false);
          }
        })
        .anyRequest()
        .authenticated()
    );
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter authentication =
      new JwtAuthenticationConverter();
    authentication.setJwtGrantedAuthoritiesConverter(roles);
    http.oauth2ResourceServer(oauth ->
      oauth
        .bearerTokenResolver(tokenResolver)
        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
    );
    http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
    return http.build();
  }
}
