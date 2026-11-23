# Backend Red Solidaria

API REST modular en Java 25 y Spring Boot 4.1.1, gestionada con Maven Wrapper.

## Configuración y ejecución normal

La base elegida debe existir y sus migraciones deben estar aplicadas. Configura explícitamente `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para el entorno autorizado (Spring Boot no carga `.env` automáticamente). No existe una URL predeterminada. Las pruebas MySQL usan exclusivamente `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` con sus controles de destino.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Hibernate usa `spring.jpa.hibernate.ddl-auto=validate`. Flyway está deshabilitado por defecto; para una migración autorizada debe habilitarse explícitamente mediante `SPRING_FLYWAY_ENABLED=true` en el entorno y destino correspondientes.

## Crear el primer administrador

El bootstrap es CLI local, sin endpoint HTTP ni cuenta predeterminada. Desde una consola interactiva de PowerShell, en esta carpeta, configura la URL exacta y carga las credenciales desde el almacén local de secretos. No las imprimas ni las guardes en Git.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/red_solidaria"
# DB_USERNAME y DB_PASSWORD deben estar cargados en el entorno local.
.\mvnw.cmd -Pbootstrap-admin spring-boot:run
```

El perfil `bootstrap-admin` selecciona la clase CLI y le entrega la opción obligatoria `--confirm-bootstrap`. El CLI fuerza `local-bootstrap` y un contexto sin servidor web. Verifica que la conexión efectiva sea MySQL local y el catálogo sea `red_solidaria`, aborta si ya hay un administrador activo y crea un rol dedicado con el único permiso `ACCESO_ADMINISTRAR`. Lee y confirma la contraseña sin eco y la almacena únicamente como BCrypt. V1 no se modifica y Flyway queda deshabilitado para este comando; Hibernate valida el esquema.

Debe ejecutarse en una consola que permita entrada oculta. Si falla la conexión, se detecta el rol reservado, ya existe un administrador, o no puede comprobar el destino local, no se deben reintentar cambios manuales de permisos; revisa [`../docs/API_ACCESO.md`](../docs/API_ACCESO.md) y corrige la causa.

Las consultas de verificación posterior que no muestran información personal ni hash están en [`../docs/API_ACCESO.md`](../docs/API_ACCESO.md).

## Organización

`pe.redsolidaria.config` contiene configuración transversal; `pe.redsolidaria.modules` separa dominios, persistencia, aplicación y API por módulo. Las respuestas HTTP usan DTO y no serializan entidades JPA. Las migraciones Flyway viven en `src/main/resources/db/migration`.
