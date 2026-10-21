package pe.redsolidaria.config;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return correo -> users.findByCorreo(correo.trim().toLowerCase(Locale.ROOT))
                .map(account -> User.withUsername(account.getCorreo())
                        .password(account.getPasswordHash())
                        .authorities(account.getRol().getAsignaciones().stream()
                                .map(assignment -> assignment.getPermiso().getCodigo()).toArray(String[]::new))
                        .disabled(!"ACTIVO".equalsIgnoreCase(account.getEstado()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales no válidas"));
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Profile("!local-bootstrap")
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/**")
                        .hasAnyAuthority("DONACION_REGISTRAR", "ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.POST, "/api/donaciones").hasAuthority("DONACION_REGISTRAR")
                        .anyRequest().authenticated())
                .build();
    }
}
