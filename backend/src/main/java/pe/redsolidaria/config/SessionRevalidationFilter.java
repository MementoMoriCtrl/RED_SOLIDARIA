package pe.redsolidaria.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rechecks every authenticated request, including changes made by the separate recovery CLI. */
final class SessionRevalidationFilter extends OncePerRequestFilter {
    private final SessionIdentityService identities;
    SessionRevalidationFilter(SessionIdentityService identities) { this.identities = identities; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            boolean valid = false;
            if (authentication.getPrincipal() instanceof SessionIdentityService.SessionUser previous) {
                try {
                    var current = (SessionIdentityService.SessionUser) identities.loadUserByUsername(previous.getUsername());
                    valid = current.isEnabled() && previous.sameRevision(current);
                    current.eraseCredentials();
                } catch (UsernameNotFoundException missing) {
                    valid = false;
                } catch (RuntimeException unavailable) {
                    SecurityConfiguration.jsonError(response, 503, "IDENTITY_CHECK_UNAVAILABLE");
                    return;
                }
            }
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                SecurityConfiguration.jsonError(response, 401, "SESSION_INVALIDATED");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
