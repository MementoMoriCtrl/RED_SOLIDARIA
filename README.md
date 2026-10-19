# Red Solidaria

Sistema web académico para la gestión y trazabilidad de donaciones ante desastres. Este repositorio separa la aplicación Angular, la API Spring Boot y la documentación del proyecto.

## Requisitos locales

- Java 25 (LTS).
- Node.js 24.15 o posterior dentro de la rama 24, y npm.
- MySQL Server 8.x.
- Git.
- Maven Wrapper incluido en `backend/`; no se necesita instalar Maven globalmente.

Versiones iniciales: Angular 22.2.x y Spring Boot 4.1.1. Consultar `docs/` para las especificaciones funcionales. La estructura inicial no equivale a que la seguridad, los módulos funcionales o la conexión a MySQL ya estén implementados.

## Estructura

```text
frontend/   Aplicación Angular con routing y CSS.
backend/    API REST Java/Spring Boot, Maven Wrapper y migraciones Flyway.
docs/       Especificación original y documentación técnica.
```

## Frontend

```powershell
cd frontend
npm ci
npm start
```

El servidor de desarrollo estará disponible en `http://localhost:4200`.

Compilación y pruebas:

```powershell
npm run build
npm test -- --watch=false
```

## Backend

Configura `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en la terminal o en el entorno de ejecución; `backend/.env.example` documenta sus nombres, pero Spring Boot no carga `.env` automáticamente. No guardes credenciales reales en archivos versionados. Crea previamente la base de datos vacía `red_solidaria` en MySQL Workbench o con `CREATE DATABASE red_solidaria CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`. No se incluyen credenciales ni datos de ejemplo.

Desde PowerShell:

```powershell
cd backend
$env:DB_URL = "jdbc:mysql://localhost:3306/red_solidaria"
$env:DB_USERNAME = "<tu usuario local>"
$env:DB_PASSWORD = "<tu contraseña local>"
.\mvnw.cmd spring-boot:run
```

Compilación y pruebas:

```powershell
.\mvnw.cmd verify
```

Flyway aplicará las migraciones al iniciar una vez configurada y disponible una conexión real a MySQL. La plantilla inicial no afirmará conectividad si el servidor o las variables no están configurados.

## API

La API se ejecutará en `http://localhost:8080`. Los endpoints, contratos OpenAPI y módulos se documentarán durante la implementación de la API. Angular se comunicará con el backend mediante HTTP/JSON.

## Git

El repositorio se inicializa localmente durante la etapa inicial. No se crean commits ni se configura un remoto automáticamente.
