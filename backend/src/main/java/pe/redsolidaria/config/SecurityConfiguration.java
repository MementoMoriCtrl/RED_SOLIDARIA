package pe.redsolidaria.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.MediaType;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public SessionIdentityService userDetailsService(UserRepository users) {
        return new SessionIdentityService(users);
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Profile("!local-bootstrap")
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider,
            SessionIdentityService identities) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authenticationProvider(authenticationProvider)
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .usernameParameter("correo").passwordParameter("password")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((request, response, exception) -> jsonError(response, 401, "AUTHENTICATION_FAILED"))
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
                        .clearAuthentication(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> jsonError(response, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (!(exception instanceof CsrfException)) {
                                jsonError(response, 403, "ACCESS_DENIED");
                                return;
                            }
                            var auth = SecurityContextHolder.getContext().getAuthentication();
                            boolean authenticated = auth != null && auth.isAuthenticated()
                                    && !(auth instanceof AnonymousAuthenticationToken);
                            boolean login = "POST".equals(request.getMethod())
                                    && (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI());
                            if (!authenticated && !login) jsonError(response, 401, "UNAUTHENTICATED");
                            else jsonError(response, 403, "CSRF_INVALID");
                        }))
                .addFilterBefore(new SessionRevalidationFilter(identities), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.POST, "/api/recursos/**").hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/recursos/**").hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.PATCH, "/api/recursos/**").hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers("/api/recursos/**", "/api/unidades-medida/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/validate").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/{codigoTrazabilidad}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/administracion/**")
                        .hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.POST, "/api/donaciones/administracion/**")
                        .hasAuthority("ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/**")
                        .hasAnyAuthority("DONACION_REGISTRAR", "ACCESO_ADMINISTRAR")
                        .requestMatchers(HttpMethod.POST, "/api/donaciones").hasAuthority("DONACION_REGISTRAR")
                        .anyRequest().authenticated())
                .build();
    }

    static void jsonError(HttpServletResponse response, int status, String code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
