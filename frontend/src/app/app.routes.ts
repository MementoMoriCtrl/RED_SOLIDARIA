import { Routes } from '@angular/router';
import { administratorGuard, authenticatedGuard, donationRegistrationGuard } from './auth/auth.guards';

export const routes: Routes = [
  {
    path: 'donaciones/registro', canActivate: [donationRegistrationGuard],
    data: { donationPage: 'registration' },
    loadComponent: () => import('./donations/donation-registration-page.component').then(module => module.DonationRegistrationPageComponent),
  },
  {
    path: 'admin/catalogos-donaciones', canActivate: [administratorGuard],
    data: { donationPage: 'catalogs' },
    loadComponent: () => import('./donations/donation-registration-page.component').then(module => module.DonationRegistrationPageComponent),
  },
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
    path: 'admin/auditoria',
    canActivate: [administratorGuard],
    loadComponent: () => import('./audit/audit-page.component')
      .then((module) => module.AuditPageComponent),
  },
  {
    path: 'admin/emergencias',
    canActivate: [administratorGuard],
    loadComponent: () => import('./emergencies/emergency-management-page.component')
      .then((module) => module.EmergencyManagementPageComponent),
  },
  {
    path: 'admin/emergencias/:id/zonas/:zoneId/necesidades',
    canActivate: [administratorGuard],
    loadComponent: () => import('./needs/need-management-page.component')
      .then((module) => module.NeedManagementPageComponent),
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
