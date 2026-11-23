package pe.redsolidaria.modules.access;

import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.modules.access.application.AccessManagementService;
import pe.redsolidaria.modules.access.domain.*;
import pe.redsolidaria.modules.access.persistence.*;

/** Only real in-memory domain objects and mock repositories. Never creates a datasource. */
final class MasterAccountProtectionFixture {
    final UserRepository users;
    final RoleRepository roles;
    final PermissionRepository permissions;
    final AccessManagementService service;
    final Role shared, alternate, restricted, unrelated;
    final UserAccount master, ordinary, other;
    final Map<Long,Permission> catalog;
    MasterAccountProtectionFixture() {
        this(mock(UserRepository.class),mock(RoleRepository.class),mock(PermissionRepository.class),new BCryptPasswordEncoder(4));
    }
    MasterAccountProtectionFixture(UserRepository users,RoleRepository roles,PermissionRepository permissions,PasswordEncoder encoder) {
        this.users=users; this.roles=roles; this.permissions=permissions;
        service=new AccessManagementService(users,roles,permissions,encoder,mock(pe.redsolidaria.modules.audit.persistence.AuditRecordRepository.class),new tools.jackson.databind.json.JsonMapper());
        catalog=Map.of(1L,permission(1,"ACCESO_ADMINISTRAR"),2L,permission(2,"DONACION_REGISTRAR"),3L,permission(3,"RECURSOS_CONSULTAR"));
        shared=role(101,"ADMINISTRADOR",Set.of(1L,2L));
        alternate=role(102,"COORDINACION",Set.of(1L,2L));
        restricted=role(103,"ADMIN_SIN_REGISTRO",Set.of(1L));
        unrelated=role(104,"OPERADOR",Set.of(3L));
        master=account(17,shared,"master@example.test");
        ReflectionTestUtils.setField(master,"adminMaestro",true);
        ordinary=account(29,shared,"ordinary@example.test");
        other=account(31,unrelated,"other@example.test");
        var accounts=Map.of(17L,master,29L,ordinary,31L,other);
        var roleCatalog=Map.of(101L,shared,102L,alternate,103L,restricted,104L,unrelated);
        when(users.findMarkedMasterForUpdate()).thenReturn(Optional.of(master));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(accounts.get(c.getArgument(0))));
        when(users.findByCorreo(anyString())).thenAnswer(c -> accounts.values().stream()
            .filter(a -> a.getCorreo().equals(c.getArgument(0))).findFirst());
        when(roles.findByIdForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(roleCatalog.get(c.getArgument(0))));
        when(permissions.findAllById(any())).thenAnswer(c -> {
            Set<Long> ids=c.getArgument(0); return ids.stream().map(catalog::get).filter(java.util.Objects::nonNull).toList();
        });
        when(users.save(any(UserAccount.class))).thenAnswer(c -> {
            UserAccount account=c.getArgument(0);
            if (account.getId()==null) ReflectionTestUtils.setField(account,"id",99L);
            return account;
        });
        when(roles.save(any(Role.class))).thenAnswer(c -> c.getArgument(0));
    }
    private static Permission permission(long id,String code) {
        var permission=new Permission(code,null); ReflectionTestUtils.setField(permission,"id",id); return permission;
    }
    private Role role(long id,String code,Set<Long> codes) {
        var role=new Role(code,code,null); ReflectionTestUtils.setField(role,"id",id);
        codes.forEach(permissionId -> role.asignar(catalog.get(permissionId))); return role;
    }
    private static UserAccount account(long id,Role role,String email) {
        var account=new UserAccount(role,"Cuenta","Prueba",email,"unused","ACTIVO");
        ReflectionTestUtils.setField(account,"id",id); return account;
    }
    static Set<String> codes(Role role) {
        return role.getAsignaciones().stream().map(a -> a.getPermiso().getCodigo()).collect(java.util.stream.Collectors.toSet());
    }
}
