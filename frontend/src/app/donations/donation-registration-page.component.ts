import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin, of, Subscription, switchMap, timer } from 'rxjs';
import { BasicAuthService } from '../auth/basic-auth.service';
import { ResourceApiService } from '../resources/resource-api.service';
import { MeasurementUnit, ResourceItem } from '../resources/resource.model';
import { AppShellComponent } from '../shared/app-shell.component';
import { DonationAdminService } from './donation-admin.service';
import { CatalogPage, CreateDonationRequest } from './donation-admin.model';

type CatalogKind = 'donantes' | 'centros' | 'emergencias';
type CatalogTab = 'donantes' | 'centros' | 'unidades';
@Component({
  selector: 'app-donation-registration-page', standalone: true,
  imports: [FormsModule, RouterLink, AppShellComponent],
  templateUrl: './donation-registration-page.component.html',
  styleUrl: './donation-registration-page.component.css',
})
export class DonationRegistrationPageComponent implements OnInit {
  private readonly api = inject(DonationAdminService);
  private readonly resourcesApi = inject(ResourceApiService);
  private readonly auth = inject(BasicAuthService);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  get isCatalogPage(): boolean { return this.route.snapshot.data['donationPage'] === 'catalogs'; }
  readonly kinds: CatalogKind[] = ['donantes', 'centros', 'emergencias'];
  catalogs: Record<CatalogKind, CatalogPage> = {
    donantes: { content: [], page: 0, totalPages: 0 }, centros: { content: [], page: 0, totalPages: 0 }, emergencias: { content: [], page: 0, totalPages: 0 },
  };
  searchText: Record<CatalogKind, string> = { donantes: '', centros: '', emergencias: '' };
  private appliedSearch: Record<CatalogKind, string> = { donantes: '', centros: '', emergencias: '' };
  readonly catalogLoading: Record<CatalogKind, boolean> = { donantes: false, centros: false, emergencias: false };
  readonly catalogErrors: Record<CatalogKind, string | null> = { donantes: null, centros: null, emergencias: null };
  private readonly searches: Partial<Record<CatalogKind, Subscription>> = {};
  activeTab: CatalogTab = 'donantes';
  readonly tabs: ReadonlyArray<{ id: CatalogTab; label: string }> = [
    { id: 'donantes', label: 'Donantes' }, { id: 'centros', label: 'Centros de acopio' }, { id: 'unidades', label: 'Unidades de medida' },
  ];
  units: MeasurementUnit[] = [];
  unitSearch = '';
  unitsLoading = false;
  unitsError: string | null = null;
  get filteredUnits(): MeasurementUnit[] {
    const text = this.unitSearch.trim().toLocaleLowerCase();
    return this.units.filter(unit => `${unit.nombre} ${unit.codigo} ${unit.simbolo ?? ''}`.toLocaleLowerCase().includes(text));
  }
  get searching(): boolean { return this.kinds.some(kind => this.catalogLoading[kind]); }
  selectTab(tab: CatalogTab): void {
    if (this.saving) return;
    this.activeTab = tab;
    if (tab !== 'unidades') this.catalogKind = tab;
  }
  onTabKey(event: KeyboardEvent): void {
    const index = this.tabs.findIndex(tab => tab.id === this.activeTab);
    const next = event.key === 'ArrowRight' ? (index + 1) % this.tabs.length : event.key === 'ArrowLeft' ? (index + this.tabs.length - 1) % this.tabs.length
      : event.key === 'Home' ? 0 : event.key === 'End' ? this.tabs.length - 1 : -1;
    if (next < 0 || this.saving) return;
    event.preventDefault();
    this.selectTab(this.tabs[next].id);
    (event.currentTarget as HTMLElement).parentElement?.querySelector<HTMLButtonElement>(`#tab-${this.activeTab}`)?.focus();
  }
  resources: ResourceItem[] = [];
  loading = true; saving = false; error: string | null = null; success: string | null = null;
  donorId: number | null = null; emergencyId: number | null = null; centerId: number | null = null;
  location = ''; observations = '';
  lines: Array<{ recursoId: number | null; cantidad: number | null }> = [{ recursoId: null, cantidad: null }];
  created: { id: number; codigoTrazabilidad: string } | null = null;
  registrationUnconfirmed = false;
  catalogKind: 'donantes' | 'centros' = 'donantes';
  private readonly drafts = {
    donantes: { name: '', location: '', description: '' },
    centros: { name: '', location: '', description: '' },
  };
  get catalogName() { return this.drafts[this.catalogKind].name; }
  set catalogName(value: string) { this.drafts[this.catalogKind].name = value; }
  get catalogLocation() { return this.drafts[this.catalogKind].location; }
  set catalogLocation(value: string) { this.drafts[this.catalogKind].location = value; }
  get catalogDescription() { return this.drafts[this.catalogKind].description; }
  set catalogDescription(value: string) { this.drafts[this.catalogKind].description = value; }
  unitCode = ''; unitName = ''; unitSymbol = '';
  get canRegister() { return this.auth.hasPermission('DONACION_REGISTRAR'); }
  get canManageCatalogs() { return this.auth.hasPermission('ACCESO_ADMINISTRAR'); }
  ngOnInit(): void { this.load(); }
  load(): void {
    if (this.saving) return;
    for (const kind of this.kinds) { this.searches[kind]?.unsubscribe(); this.catalogErrors[kind] = null; }
    this.loading = true; this.error = null;
    if (this.isCatalogPage) this.reloadUnits();
    forkJoin({ resources: this.isCatalogPage ? of<ResourceItem[]>([]) : this.resourcesApi.resources(), donantes: this.api.catalog('donantes'),
      centros: this.api.catalog('centros'), emergencias: this.isCatalogPage ? of<CatalogPage>({ content: [], page: 0, totalPages: 0 }) : this.api.catalog('emergencias') })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { this.loading = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => {
        this.resources = result.resources.filter(resource => resource.estado === 'ACTIVO');
        for (const kind of this.kinds) { this.catalogs[kind] = result[kind]; this.searchText[kind] = ''; }
        this.donorId = this.centerId = this.emergencyId = null;
        this.appliedSearch = { donantes: '', centros: '', emergencias: '' };
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }
  selectedId(kind: CatalogKind): number | null {
    return kind === 'donantes' ? this.donorId : kind === 'centros' ? this.centerId : this.emergencyId;
  }
  selectCatalog(kind: CatalogKind, id: number | null): void {
    if (kind === 'donantes') this.donorId = id;
    else if (kind === 'centros') this.centerId = id;
    else this.emergencyId = id;
  }
  filterSelector(kind: CatalogKind, text: string): void {
    this.searchText[kind] = text;
    this.search(kind, false, 300);
  }
  search(kind: CatalogKind, more = false, debounce = 0): void {
    if (this.loading || this.saving) return;
    const text = this.searchText[kind].trim();
    const append = more && text === this.appliedSearch[kind];
    const page = append ? this.catalogs[kind].page + 1 : 0;
    if (append && page >= this.catalogs[kind].totalPages) return;
    this.searches[kind]?.unsubscribe();
    if (!append) this.selectCatalog(kind, null);
    this.catalogLoading[kind] = true; this.catalogErrors[kind] = null;
    const request = debounce ? timer(debounce).pipe(switchMap(() => this.api.catalog(kind, text, page))) : this.api.catalog(kind, text, page);
    this.searches[kind] = request
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { this.catalogLoading[kind] = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => {
        this.appliedSearch[kind] = text;
        this.catalogs[kind] = { ...result, content: append
          ? [...new Map([...this.catalogs[kind].content, ...result.content].map(item => [item.id, item])).values()] : result.content };
        if (!append) {
          if (kind === 'donantes') this.donorId = null;
          if (kind === 'centros') this.centerId = null;
          if (kind === 'emergencias') this.emergencyId = null;
        }
      }, error: (response: HttpErrorResponse) => { this.catalogErrors[kind] = this.message(response); } });
  }
  createCatalog(): void {
    if (this.saving || this.loading || this.searching || !this.isCatalogPage || !this.canManageCatalogs) return;
    if (!this.catalogName.trim() || this.catalogName.trim().length > 150 || this.catalogLocation.length > 200
        || new TextEncoder().encode(this.catalogDescription).length > 65535) { this.error = 'Revisa nombre, ubicación y descripción.'; return; }
    const kind = this.catalogKind;
    this.saving = true; this.error = null; this.success = null;
    this.api.createCatalog(kind, { nombre: this.catalogName.trim(), ubicacion: this.catalogLocation.trim(), descripcion: this.catalogDescription.trim() })
      .pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: item => {
        this.catalogs[kind].content = [...this.catalogs[kind].content, item];
        this.catalogErrors[kind] = null;
        if (kind === 'donantes') this.donorId = item.id; else this.centerId = item.id;
        this.catalogName = this.catalogLocation = this.catalogDescription = '';
        this.success = 'Registro añadido al catálogo.';
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }
  createUnit(): void {
    if (this.saving || this.loading || !this.isCatalogPage || !this.canManageCatalogs) return;
    if (!/^[A-Za-z0-9_-]{1,30}$/.test(this.unitCode) || !this.unitName.trim() || this.unitName.length > 80 || this.unitSymbol.length > 20) {
      this.error = 'Revisa código, nombre y símbolo de la unidad.'; return;
    }
    this.saving = true; this.error = null;
    this.api.createUnit({ codigo: this.unitCode, nombre: this.unitName.trim(), simbolo: this.unitSymbol })
      .pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: () => { this.success = 'Unidad registrada. Ya puedes seleccionarla al crear recursos.';
        this.unitCode = this.unitName = this.unitSymbol = ''; this.unitSearch = ''; this.reloadUnits();
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }
  reloadUnits(): void {
    if (this.unitsLoading) return;
    this.unitsLoading = true; this.unitsError = null;
    this.resourcesApi.measurementUnits().pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => { this.unitsLoading = false; this.cd.markForCheck(); }))
      .subscribe({ next: units => this.units = units, error: (response: HttpErrorResponse) => this.unitsError = this.message(response) });
  }
  register(): void {
    if (this.loading || this.saving || this.searching || this.isCatalogPage || !this.canRegister || this.created || this.registrationUnconfirmed) return;
    const ids = this.lines.map(line => line.recursoId);
    if (!this.donorId || (!this.emergencyId && !this.centerId) || !this.lines.length || new Set(ids).size !== ids.length
        || this.lines.some(line => !line.recursoId || !this.resources.some(resource => resource.id === line.recursoId)
          || line.cantidad === null || !Number.isFinite(line.cantidad) || line.cantidad <= 0 || line.cantidad > 99999999999.999
          || !/^\d+(\.\d{1,3})?$/.test(String(line.cantidad))) || this.location.length > 200
        || new TextEncoder().encode(this.observations).length > 65535) {
      this.error = 'Indica donante, destino y recursos únicos con cantidades positivas de hasta tres decimales.'; return;
    }
    const request: CreateDonationRequest = { donanteId: this.donorId, emergenciaId: this.emergencyId,
      centroAcopioId: this.centerId, ubicacion: this.location.trim(), observaciones: this.observations.trim(),
      detalles: this.lines.map(line => ({ recursoId: line.recursoId!, cantidad: line.cantidad! })) };
    this.saving = true; this.error = null; this.success = null;
    this.api.register(request).pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => { this.created = result; this.success = `Donación registrada: ${result.codigoTrazabilidad}. Aún no se ha recibido ni entregado.`; },
        error: (response: HttpErrorResponse) => {
          this.registrationUnconfirmed = response.status === 0 || response.status >= 500;
          this.error = this.registrationUnconfirmed ? 'El registro no fue confirmado. Verifica el listado con un administrador antes de repetirlo para evitar duplicados.' : this.message(response);
        } });
  }
  newDonation(): void {
    if (this.saving || this.registrationUnconfirmed) return;
    this.created = null; this.lines = [{ recursoId: null, cantidad: null }]; this.success = null; this.error = null;
    this.location = ''; this.observations = '';
  }
  private message(response: HttpErrorResponse): string {
    if (response.status === 401) return 'Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para esta operación.';
    if (response.status === 400 || response.status === 409) return 'Revisa los datos; el registro no cumple las validaciones.';
    return 'No se pudo completar la operación. Revisa la conexión y recarga los catálogos.';
  }
}
