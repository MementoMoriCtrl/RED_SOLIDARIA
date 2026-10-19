# Backend Red Solidaria

API REST modular en Java 25 y Spring Boot 4.1.1, gestionada con Maven Wrapper.

## Configuración local

1. Crea una base de datos vacía llamada `red_solidaria` en MySQL.
2. Configura `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en el entorno de la terminal. `../backend/.env.example` muestra los nombres; Spring Boot no carga archivos `.env` automáticamente.
3. Ejecuta `.\mvnw.cmd spring-boot:run`.

La plantilla incluye Spring Security, pero todavía no define inicio de sesión, usuarios, roles ni permisos propios de Red Solidaria. No debe considerarse que la autenticación del sistema esté implementada.

## Organización

`pe.redsolidaria.config` y `pe.redsolidaria.common` contienen configuración y soporte transversal. Los dominios viven en `pe.redsolidaria.modules`; cuando se implemente cada módulo, sus responsabilidades se separarán en `controller`, `service`, `repository`, `entity` y `dto`. No se generan aún entidades funcionales.

Las migraciones versionadas se ubican en `src/main/resources/db/migration`. La documentación HTTP se preparó con Spring REST Docs para incorporarse junto con pruebas de endpoints.
