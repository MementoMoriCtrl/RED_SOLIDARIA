package pe.redsolidaria.modules.access.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

public final class AccessDtos {
    private AccessDtos() {}

    public record UserResponse(Long id, String nombre, String apellido, String correo,
                               String estado, Long rolId, String rolCodigo, String rolNombre,
                               LocalDateTime creadoEn, LocalDateTime actualizadoEn) {}
    public record RoleResponse(Long id, String codigo, String nombre, String descripcion,
                               LocalDateTime creadoEn, Set<String> permisos) {}
    public record PermissionResponse(Long id, String codigo, String descripcion,
                                     LocalDateTime creadoEn) {}

    public record CreateUserRequest(@NotNull Long rolId, @NotBlank @Size(max = 100) String nombre,
                                    @NotBlank @Size(max = 100) String apellido,
                                    @NotBlank @Email @Size(max = 150) String correo,
                                    @NotBlank @Size(min = 12, max = 128) String password,
                                    @NotBlank @Size(max = 20) String estado) {
        @Override public String toString() {
            return "CreateUserRequest[rolId=" + rolId + ", nombre=" + nombre + ", apellido=" + apellido
                    + ", correo=" + correo + ", password=[REDACTED], estado=" + estado + "]";
        }
    }
    public record ChangeUserStateRequest(@NotBlank @Size(max = 20) String estado) {}
    public record ChangeUserRoleRequest(@NotNull Long rolId) {}
    public record CreateRoleRequest(@NotBlank @Size(max = 50) String codigo,
                                    @NotBlank @Size(max = 100) String nombre,
                                    @Size(max = 255) String descripcion) {}
    public record ReplaceRolePermissionsRequest(@NotNull Set<@NotNull Long> permisoIds) {}
    public record CreatePermissionRequest(@NotBlank @Size(max = 80) String codigo,
                                          @Size(max = 255) String descripcion) {}
}
