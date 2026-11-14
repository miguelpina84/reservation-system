package com.example.reservation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.reservation.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties
public class SecurityConfig {

    /** endpoints de solo lectura: públicos. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/rooms/**",
            "/api/reservations/**",
            "/actuator/health"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Usuarios en memoria (configurables en application.yml). En producción se sustituiría por
     * un IdP (LDAP/OIDC) manteniendo el resto de la configuración.
     */
    @Bean
    public UserDetailsService userDetailsService(Environment environment, PasswordEncoder passwordEncoder) {
        List<UserDetails> users = new ArrayList<>();
        int index = 0;
        while (true) {
            String username = environment.getProperty("app.security.users[" + index + "].username");
            if (username == null) {
                break;
            }
            String password = environment.getProperty("app.security.users[" + index + "].password", "");
            String roles = environment.getProperty("app.security.users[" + index + "].roles", "USER");
            users.add(User.withUsername(username)
                    .password(passwordEncoder.encode(password))
                    .roles(roles.split("\\s*,\\s*"))
                    .build());
            index++;
        }
        if (users.isEmpty()) {
            users.add(User.withUsername("user")
                    .password(passwordEncoder.encode("user"))
                    .roles("USER")
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // lectura pública
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/rooms/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reservations/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // escritura autenticada
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/rooms/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/reservations/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/reservations/**").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            ApiError body = new ApiError(Instant.now(), 401, "Unauthorized",
                                    "Se requieren credenciales para esta operación", request.getRequestURI(), Map.of());
                            objectMapper.writeValue(response.getOutputStream(), body);
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            ApiError body = new ApiError(Instant.now(), 403, "Forbidden",
                                    "No tiene permisos para esta operación", request.getRequestURI(), Map.of());
                            objectMapper.writeValue(response.getOutputStream(), body);
                        }));
        return http.build();
    }
}
