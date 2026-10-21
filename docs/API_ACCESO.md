# API de usuarios, roles y permisos

La API usa HTTP Basic sin sesión, valida credenciales contra `usuario` y genera autoridades desde `rol_permiso`/`permiso`. Las contraseñas se codifican con BCrypt (coste 12). Los endpoints administrativos devuelven DTO, nunca `password_hash`.

No existe un endpoint público de bootstrap, cuenta semilla ni contraseña predeterminada. La aplicación normal no ejecuta la herramienta CLI.

## Endpoints

Todos requieren `Authorization: Basic ...` con un usuario cuyo estado sea `ACTIVO` y cuyo rol tenga `ACCESO_ADMINISTRAR`.

| Método | Ruta | Función |
|---|---|---|
| GET | `/api/admin/usuarios` | Lista usuarios sin hashes |
| POST | `/api/admin/usuarios` | Crea usuario; BCrypt codifica la contraseña |
| PATCH | `/api/admin/usuarios/{id}/estado` | Cambia el estado de un usuario |
| PUT | `/api/admin/usuarios/{id}/rol` | Asigna un rol existente |
| GET | `/api/admin/roles` | Lista roles con sus códigos de permiso |
| POST | `/api/admin/roles` | Crea un rol |
| PUT | `/api/admin/roles/{id}/permisos` | Reemplaza permisos de un rol |
| GET | `/api/admin/permisos` | Lista permisos |
| POST | `/api/admin/permisos` | Crea un permiso |

La API rechaza asignar `ACCESO_ADMINISTRAR` a un rol que aún no lo posee. Solo permite conservarlo en el rol que ya lo tiene. Los demás endpoints también exigen esa autoridad; no se encontró un servicio o ruta HTTP alternativo para elevar permisos.

## Política de contraseñas

Las contraseñas deben tener entre 12 y 128 puntos de código y no superar 72 bytes UTF-8, límite efectivo de BCrypt. El mismo validador se aplica al alta por API y al CLI. La política de 12 caracteres es la decisión técnica inicial documentada; debe revisarse si el documento funcional establece otra.

## Bootstrap del primer administrador

El bootstrap crea un rol nuevo y dedicado `ADMINISTRADOR`; nunca reutiliza un rol existente. Le asigna únicamente `ACCESO_ADMINISTRAR`, creando ese permiso si no existe, y no altera asignaciones previas. Si ya existe un usuario `ACTIVO` con esa autoridad, ya existe el rol reservado o el correo está ocupado, cancela sin reasignar permisos.

La herramienta requiere una terminal interactiva, un MySQL local con la base `red_solidaria`, las variables locales `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, y que V1 ya esté aplicada. El perfil CLI deshabilita Flyway y HTTP, mantiene Hibernate en `ddl-auto=validate` y confirma el catálogo y URL efectivos antes de escribir. No acepta destino remoto ni URL distinta.

Desde PowerShell, en `backend`:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/red_solidaria"
# DB_USERNAME y DB_PASSWORD deben estar cargados desde el almacén local de secretos.
.\mvnw.cmd -Pbootstrap-admin spring-boot:run
```

El perfil Maven selecciona `BootstrapAdminCli` y le pasa `--confirm-bootstrap`. El CLI exige exactamente esa confirmación, activa `local-bootstrap`, arranca sin servidor HTTP y solicita correo, nombre, apellido y contraseña dos veces. La contraseña se lee sin eco, no va en argumentos ni logs, se limpia de los arreglos de entrada y se almacena solo con el `PasswordEncoder` BCrypt existente. Todas las escrituras van en una transacción `SERIALIZABLE`; un fallo revierte rol, permiso y usuario.

Si no hay consola interactiva, no se puede verificar el destino, o los requisitos previos no se cumplen, el proceso aborta. No se debe ejecutar desde una terminal que registre entrada sensible.

### Verificación posterior sin exponer datos

En MySQL Workbench, confirma solo los conteos y el tipo de hash; estas consultas no muestran correo ni hash:

```sql
SELECT COUNT(DISTINCT u.id_usuario) AS administradores_activos
FROM usuario u
JOIN rol r ON r.id_rol = u.id_rol
JOIN rol_permiso rp ON rp.id_rol = r.id_rol
JOIN permiso p ON p.id_permiso = rp.id_permiso
WHERE r.codigo = 'ADMINISTRADOR'
  AND u.estado = 'ACTIVO'
  AND p.codigo = 'ACCESO_ADMINISTRAR';

SELECT COUNT(*) AS permisos_asignados
FROM rol_permiso rp
JOIN rol r ON r.id_rol = rp.id_rol
WHERE r.codigo = 'ADMINISTRADOR';

SELECT COUNT(*) AS hashes_bcrypt
FROM usuario u
JOIN rol r ON r.id_rol = u.id_rol
WHERE r.codigo = 'ADMINISTRADOR'
  AND (u.password_hash LIKE '$2a$%' OR u.password_hash LIKE '$2b$%' OR u.password_hash LIKE '$2y$%');
```

Los resultados esperados son `1`, `1` y `1`. No consultes ni copies el valor de `password_hash`.

## Pruebas

Desde `backend`, ejecuta `.\mvnw.cmd test` en PowerShell. Las pruebas unitarias y MVC no necesitan MySQL. Las pruebas `AccessRepositoryIntegrationTest`, `BootstrapRollbackMySqlTest` y el arranque de contexto requieren `DB_USERNAME` y una conexión válida; están condicionadas por esa variable y se omiten si falta. Antes de ejecutarlas, confirma que `DB_URL` apunte a `red_solidaria` local.

Las pruebas MySQL crean filas temporales en transacciones revertidas. Incluyen persistencia y hash, autenticación válida/inválida desde el repositorio real y rollback ante fallo deliberado de BCrypt. Si ya hay un administrador válido o el rol reservado existe, el caso de rollback se omite para no alterar esos datos.

## Pendiente

- Confirmar el catálogo funcional de estados de usuario; hoy la autenticación habilita únicamente `ACTIVO`.
- Confirmar los códigos de permisos correspondientes a cada pantalla y operación administrativa.
- Configurar TLS en entornos donde se use HTTP Basic.
- Ejecutar el CLI y las pruebas de persistencia/rollback contra el MySQL local; no se consideran verificadas hasta completar esas comprobaciones.
