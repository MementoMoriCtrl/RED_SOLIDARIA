# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.2.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Donación: consulta de trazabilidad

La ruta `/trazabilidad` permite consultar una donación por su código sin iniciar sesión. Durante `ng serve`, las peticiones a `/api/**` se reenvían a `http://localhost:8080` mediante `proxy.conf.json`, por lo que el navegador conserva el mismo origen y no necesita CORS para desarrollo local.

La ruta pública es `GET /api/donaciones/{codigoTrazabilidad}`. El acceso privado utiliza una sesión de servidor bajo el mismo origen. El frontend obtiene un token CSRF y lo envía en escrituras dirigidas a la API del mismo origen; la contraseña no se conserva después del login.
## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
