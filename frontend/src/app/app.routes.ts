import { Routes } from '@angular/router';
import { administratorGuard, authenticatedGuard } from './auth/auth.guards';

export const routes: Routes = [
  {
    path: 'acceso-organizaciones',
    loadComponent: () => import('./auth/organization-login-page.component')
      .then((module) => module.OrganizationLoginPageComponent),
  },
  {
    path: 'admin',
    canActivate: [administratorGuard],
    loadComponent: () => import('./admin/admin-dashboard-page.component')
      .then((module) => module.AdminDashboardPageComponent),
  },
  {
    path: 'admin/donaciones',
    canActivate: [administratorGuard],
    loadComponent: () => import('./donations/donation-administration-page.component')
      .then((module) => module.DonationAdministrationPageComponent),
  },
  {
    path: 'admin/donaciones/:id',
    canActivate: [administratorGuard],
    loadComponent: () => import('./donations/donation-administration-detail-page.component')
      .then((module) => module.DonationAdministrationDetailPageComponent),
  },
  {
    path: 'recursos',
    canActivate: [authenticatedGuard],
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
