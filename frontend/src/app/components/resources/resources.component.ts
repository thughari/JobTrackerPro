import { Component, HostListener, computed, inject, signal, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { CareerResource, ResourceService } from '../../services/resource.service';
import { LogoComponent } from '../ui/logo/logo.component';

const PAGE_SIZE = 20;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-resources',
  standalone: true,
  imports: [CommonModule, RouterLink, LogoComponent, FormsModule],
  templateUrl: './resources.component.html',
  styleUrl: './resources.component.css',
})
export class ResourcesComponent implements OnDestroy {
  authService = inject(AuthService);
  private resourceService = inject(ResourceService);
  private router = inject(Router);

  @ViewChild('fileInput') fileInputVariable?: ElementRef;

  // --- STATE SIGNALS ---
  readonly resources = signal<CareerResource[]>([]);
  readonly myResources = signal<CareerResource[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hasNext = signal(false);
  readonly currentPage = signal(0);
  readonly viewMode = signal<'community' | 'mine'>('community');
  
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  private messageTimeout: any;

  // UI Modals
  readonly showAddResourceModal = signal(false);
  readonly showDeleteConfirm = signal(false);
  readonly resourceToDelete = signal<CareerResource | null>(null);
  
  readonly isSaving = signal(false);
  readonly isDeleting = signal(false);
  readonly isInAppRoute = signal(false);

  // Filters
  readonly searchQuery = signal('');
  readonly selectedCategoryFilter = signal('');
  readonly backendCategories = signal<string[]>([]);
  readonly categoryOptions = computed(() => this.backendCategories());

  // Add Form Models
  title = '';
  url = '';
  category = '';
  description = '';
  contributionMode: 'link' | 'file' = 'link';
  selectedFile: File | null = null;
  selectedFileError = '';

  private searchDebounceRef: any = null;
  private scrollLoadDebounceRef: any = null;

  // --- COMPUTED DATA ---
  readonly displayResources = computed(() => {
    return this.viewMode() === 'community' ? this.resources() : this.myResources();
  });

  readonly groupedResources = computed(() => {
    const data = this.displayResources();
    const grouped = new Map<string, CareerResource[]>();
    for (const resource of data) {
      const key = resource.category?.trim() || 'General';
      const list = grouped.get(key) ?? [];
      list.push(resource);
      grouped.set(key, list);
    }
    return Array.from(grouped.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([category, links]) => ({ category, links }));
  });

  constructor() {
    this.syncRouteContext();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.syncRouteContext());
    this.loadCategoryOptions();
    this.loadResources(true);
    if (this.authService.isAuthenticated()) this.loadMyResources();
  }

  ngOnDestroy() {
    if (this.messageTimeout) clearTimeout(this.messageTimeout);
    if (this.searchDebounceRef) clearTimeout(this.searchDebounceRef);
  }

  private syncRouteContext() {
    this.isInAppRoute.set(this.router.url.startsWith('/app/'));
  }

  private resetAddForm() {
    this.title = '';
    this.url = '';
    this.category = '';
    this.description = '';
    this.contributionMode = 'link';
    this.selectedFile = null;
    this.selectedFileError = '';
    if (this.fileInputVariable) {
      this.fileInputVariable.nativeElement.value = "";
    }
  }

  // --- TAB & DATA LOADING ---
  setViewMode(mode: 'community' | 'mine') {
    this.viewMode.set(mode);
    this.currentPage.set(0);
    if (mode === 'mine') this.loadMyResources();
    else this.loadResources(true);
  }

  async loadResources(reset = false) {
    if (reset) { this.currentPage.set(0); this.resources.set([]); this.isLoading.set(true); }
    else { this.isLoadingMore.set(true); }

    try {
      const page = this.currentPage();
      const filters = { query: this.searchQuery(), category: this.selectedCategoryFilter(), type: 'all' as any };
      const response = await this.resourceService.getResources(page, PAGE_SIZE, filters, reset);
      this.resources.update(prev => reset ? response.content : [...prev, ...response.content]);
      this.hasNext.set(response.hasNext);
      this.currentPage.set(page + 1);
    } catch {
      this.showMessage('error', 'Could not load resources.');
    } finally {
      this.isLoading.set(false);
      this.isLoadingMore.set(false);
    }
  }

  async loadMyResources() {
    try {
      const mine = await this.resourceService.getMyResources();
      this.myResources.set(mine);
    } catch {
      this.showMessage('error', 'Could not load your shares.');
    }
  }

  async loadCategoryOptions(force = false) {
    const cats = await this.resourceService.getResourceCategories(force);
    this.backendCategories.set(cats);
  }

  // --- CRUD ACTIONS ---
  async addResource() {
    if (!this.title.trim() || !this.category.trim()) return;
    this.isSaving.set(true);
    try {
      if (this.contributionMode === 'file' && this.selectedFile) {
        await this.resourceService.uploadResourceFile({ 
          title: this.title, category: this.category, description: this.description, file: this.selectedFile 
        });
      } else {
        await this.resourceService.createResource({ 
          title: this.title, url: this.url, category: this.category, description: this.description 
        });
      }
      this.showMessage('success', 'Resource shared with the community!');
      this.loadResources(true);
      this.loadMyResources();
      this.closeAddResourceModal();
      this.loadCategoryOptions(true);
    } catch (err: any) {
      this.showMessage('error', err.error?.message || 'Upload failed.');
    } finally {
      this.isSaving.set(false);
    }
  }

  onDeleteClick(resource: CareerResource) {
    this.resourceToDelete.set(resource);
    this.showDeleteConfirm.set(true);
  }

  async confirmDelete() {
    const res = this.resourceToDelete();
    if (!res) return;
    this.isDeleting.set(true);
    try {
      await this.resourceService.deleteResource(res.id);
      this.resources.update(p => p.filter(item => item.id !== res.id));
      this.myResources.update(p => p.filter(item => item.id !== res.id));
      this.showMessage('success', 'Resource removed.');
      this.closeDeleteModal();
    } catch {
      this.showMessage('error', 'Delete failed.');
    } finally {
      this.isDeleting.set(false);
    }
  }

  // --- UI HELPERS ---
  onSearchQueryChange(value: string) {
    this.searchQuery.set(value);
    if (this.searchDebounceRef) clearTimeout(this.searchDebounceRef);
    this.searchDebounceRef = setTimeout(() => this.loadResources(true), 400);
  }

  onCategoryFilterChange(value: string) {
    this.selectedCategoryFilter.set(value);
    this.loadResources(true);
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (file && file.size > MAX_FILE_SIZE_BYTES) {
      this.selectedFileError = 'File exceeds 10MB limit.';
      this.selectedFile = null;
      return;
    }
    this.selectedFile = file;
    this.selectedFileError = '';
  }

  showMessage(type: 'success' | 'error', msg: string) {
    this.clearMessages();
    if (type === 'success') this.successMessage.set(msg);
    else this.errorMessage.set(msg);
    this.messageTimeout = setTimeout(() => this.clearMessages(), 5000);
  }

  clearMessages() { this.successMessage.set(''); this.errorMessage.set(''); }
  openAddResourceModal() { this.showAddResourceModal.set(true); }
  closeAddResourceModal() { this.showAddResourceModal.set(false); this.resetAddForm(); }
  closeDeleteModal() { this.showDeleteConfirm.set(false); this.resourceToDelete.set(null); }
  onModeChange(mode: 'link' | 'file') { this.contributionMode = mode; }
  formatFileSize(s?: number) { return s ? (s < 1048576 ? `${Math.ceil(s/1024)}KB` : `${(s/1048576).toFixed(1)}MB`) : ''; }

  @HostListener('window:scroll')
  onWindowScroll() {
    if (this.viewMode() === 'mine' || this.showAddResourceModal() || this.showDeleteConfirm()) return;
    if (!this.hasNext() || this.isLoading() || this.isLoadingMore()) return;

    const pos = (document.documentElement.scrollTop || document.body.scrollTop) + document.documentElement.offsetHeight;
    const max = document.documentElement.scrollHeight;
    if (max - pos < 300) {
      if (this.scrollLoadDebounceRef) return;
      this.scrollLoadDebounceRef = setTimeout(() => {
        this.scrollLoadDebounceRef = null;
        this.loadResources();
      }, 400);
    }
  }
}