import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'acceso-organizaciones',
    loadComponent: () => import('./auth/organization-login-page.component')
      .then((module) => module.OrganizationLoginPageComponent),
  },
  {
    path: 'recursos',
    loadComponent: () => import('./resources/resource-management-page.component')
      .then((module) => module.ResourceManagementPageComponent),
  },
  {
    path: 'trazabilidad',
    loadComponent: () => import('./donations/donation-trace-page.component')
      .then((module) => module.DonationTracePageComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'trazabilidad' },
  { path: '**', redirectTo: 'trazabilidad' },
];
