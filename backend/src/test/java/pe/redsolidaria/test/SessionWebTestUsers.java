package pe.redsolidaria.test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.support.WebApplicationContextUtils;
import pe.redsolidaria.config.SessionIdentityService;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Test-only identity fixture; the actual session identity service and security filters still execute. */
public final class SessionWebTestUsers {
    private SessionWebTestUsers() {}
    public static TestUser user(String name) { return new TestUser(name); }

    public static final class TestUser implements RequestPostProcessor {
        private final String name;
        private List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        private TestUser(String name) { this.name = name; }
        public TestUser authorities(GrantedAuthority... values) { authorities = Arrays.asList(values); return this; }
        @Override public org.springframework.mock.web.MockHttpServletRequest postProcessRequest(
                org.springframework.mock.web.MockHttpServletRequest request) {
            var context = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
            var users = context.getBean(UserRepository.class);
            var identities = context.getBean(SessionIdentityService.class);
            String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
            if (users.findByCorreo(normalized).isEmpty()) {
                var role = new Role("TEST", "Test", null);
                for (var authority : authorities) role.asignar(new Permission(authority.getAuthority(), null));
                var account = new UserAccount(role, "Test", "User", normalized, "test-only", "ACTIVO");
                when(users.findByCorreo(normalized)).thenReturn(Optional.of(account));
            }
            var principal = (SessionIdentityService.SessionUser) identities.loadUserByUsername(normalized);
            principal.eraseCredentials();
            var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
            csrf().postProcessRequest(request);
            return authentication(auth).postProcessRequest(request);
        }
    }
}
