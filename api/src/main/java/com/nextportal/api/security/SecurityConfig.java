package com.nextportal.api.security;

import com.nextportal.common.member.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean JwtEncoder jwtEncoder(SecurityProperties properties) {
        var key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
    }
    @Bean JwtDecoder jwtDecoder(SecurityProperties properties) {
        var key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()),
            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("next-portal"))));
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, SessionStore sessions, MemberRepository members, PortalAuthorization authorization,
                                      @Value("${portal.docs.enabled:false}") boolean documentationEnabled) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // Bearer-only APIs; refresh cookie is protected by exact Origin checks.
            .cors(Customizer.withDefaults()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                    .access((authentication, context) -> new AuthorizationDecision(documentationEnabled))
                .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/refresh").permitAll()
                .requestMatchers("/actuator/health", "/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("admin:access")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                var session = Optional.ofNullable(token.getClaimAsString("sid")).flatMap(sessions::find)
                    .filter(value -> value.accessId().equals(token.getId()))
                    .filter(value -> Long.toString(value.memberId()).equals(token.getSubject()))
                    .orElseThrow(SecurityConfig::invalidSession);
                var member = members.findById(session.memberId())
                    .filter(value -> value.getStatus() == MemberStatus.ACTIVE)
                    .filter(value -> value.getAuthVersion() == session.version())
                    .filter(value -> Long.valueOf(value.getAuthVersion()).equals(token.getClaim("ver")))
                    .orElseThrow(SecurityConfig::invalidSession);
                var grants = authorization.forMember(member.getId());
                Optional.of(grants.version())
                    .filter(value -> value.equals(session.authorizationVersion()) && value.equals(token.getClaimAsString("authz")))
                    .orElseThrow(SecurityConfig::invalidSession);
                var authorities = java.util.stream.Stream.concat(
                    grants.roles().stream().map(value -> "ROLE_" + value), grants.permissions().stream())
                    .map(SimpleGrantedAuthority::new).toList();
                return new JwtAuthenticationToken(token, authorities);
            }))).build();
    }

    private static OAuth2AuthenticationException invalidSession() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }

    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
