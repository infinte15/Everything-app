package com.Finn.everything_app.security;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    /** Schaltet zusammen mit {@code DevAuthController} den passwortlosen Dev-Login frei. */
    @Value("${app.dev-login.enabled:false}")
    private boolean devLoginEnabled;

    /** Muss zum gleichnamigen Flag im {@code AuthController} passen. */
    @Value("${app.registration.enabled:false}")
    private boolean registrationEnabled;

    /**
     * Kommagetrennte Liste erlaubter Origins. Leer = gar keine Cross-Origin-Freigabe.
     *
     * <p>Im Produktionsbetrieb liegen Web-App und API hinter Caddy auf derselben Origin, dann
     * braucht es CORS nicht. Die nativen Clients sind keine Browser und ignorieren CORS ohnehin.
     */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/login", "/error").permitAll();

                    // Rücksprung aus dem Bank-Login: der Browser kommt ohne JWT zurück.
                    // Die Zuordnung zum Nutzer läuft über den einmalig verwendbaren
                    // state-Parameter, nicht über den Authorization-Header.
                    auth.requestMatchers("/api/finance/bank/callback").permitAll();

                    if (registrationEnabled) {
                        auth.requestMatchers("/api/auth/register").permitAll();
                    }
                    if (devLoginEnabled) {
                        auth.requestMatchers("/api/auth/dev-login").permitAll();
                    }

                    // Alle anderen Endpoints benötigen Authentifizierung
                    auth.anyRequest().authenticated();
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(daoAuthenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Bewusst KEIN {@code @Bean}: sobald ein AuthenticationProvider als Bean im Kontext liegt,
     * schaltet Spring Security die automatische Verdrahtung von UserDetailsService und
     * PasswordEncoder für den globalen AuthenticationManager ab und warnt bei jedem Start
     * davor. Als lokale Instanz hängt der Provider nur an dieser Filterkette; den
     * AuthenticationManager für /api/auth/login baut Spring aus den beiden Beans selbst —
     * mit demselben DaoAuthenticationProvider, nur ohne die Warnung.
     *
     * Der UserDetailsService kommt seit Spring Security 6.5 über den Konstruktor; der
     * parameterlose Konstruktor und setUserDetailsService sind deprecated.
     */
    private AuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return source;   // keine Regel registriert -> keine Freigabe
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}