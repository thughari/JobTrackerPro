import { CommonModule } from '@angular/common';
import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { CareerResource, ResourceQueryFilters, ResourceService } from '../../services/resource.service';
import { LogoComponent } from '../ui/logo/logo.component';

const PAGE_SIZE = 20;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
type ResourceTypeFilter = 'all' | 'LINK' | 'FILE';

@Component({
  selector: 'app-resources',
  standalone: true,
  imports: [CommonModule, RouterLink, LogoComponent, FormsModule],
  templateUrl: './resources.component.html',
  styleUrl: './resources.component.css'
})
export class ResourcesComponent {
  authService = inject(AuthService);
  private resourceService = inject(ResourceService);
  private router = inject(Router);

  readonly resources = signal<CareerResource[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hasNext = signal(false);
  readonly currentPage = signal(0);
  readonly errorMessage = signal('');
  readonly saveMessage = signal('');
  readonly editMessage = signal('');
  readonly isSaving = signal(false);
  readonly isUpdating = signal(false);
  readonly isInAppRoute = signal(false);
  readonly showAddResourceModal = signal(false);
  readonly isLoadingMyResources = signal(false);
  readonly myResources = signal<CareerResource[]>([]);
  readonly editingResource = signal<CareerResource | null>(null);
  private searchDebounceRef: ReturnType<typeof setTimeout> | null = null;
  private scrollLoadDebounceRef: ReturnType<typeof setTimeout> | null = null;

  readonly searchQuery = signal('');
  readonly selectedCategoryFilter = signal('all');
  readonly selectedTypeFilter = signal<ResourceTypeFilter>('all');
  readonly backendCategories = signal<string[]>([]);

  title = '';
  url = '';
  category = '';
  description = '';
  contributionMode: 'link' | 'file' = 'link';
  selectedFile: File | null = null;
  selectedFileError = '';

  editTitle = '';
  editUrl = '';
  editCategory = '';
  editDescription = '';

  readonly categoryOptions = computed(() => ['all', ...this.backendCategories()]);

  onSearchQueryChange(value: string) {
    this.searchQuery.set(value);
    this.scheduleFilterReload();
  }

  onCategoryFilterChange(value: string) {
    const normalized = value.trim();
    this.selectedCategoryFilter.set(normalized ? normalized : 'all');
    this.loadResources(true);
  }

  onTypeFilterChange(value: ResourceTypeFilter) {
    this.selectedTypeFilter.set(value);
    this.loadResources(true);
  }

  readonly groupedResources = computed(() => {
    const grouped = new Map<string, CareerResource[]>();

    for (const resource of this.resources()) {
      const key = resource.category?.trim() || 'General';
      const list = grouped.get(key) ?? [];
      list.push(resource);
      grouped.set(key, list);
    }

    return Array.from(grouped.entries()).map(([category, links]) => ({
      category,
      links
    }));
  });

  constructor() {
    this.syncRouteContext();
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.syncRouteContext());

    this.loadCategoryOptions();
    this.loadResources(true);
    if (this.authService.isAuthenticated()) {
      this.loadMyResources();
    }
  }

  private syncRouteContext() {
    this.isInAppRoute.set(this.router.url.startsWith('/app/'));
  }


  async loadCategoryOptions(forceRefresh = false) {
    try {
      const categories = await this.resourceService.getResourceCategories(forceRefresh);
      this.backendCategories.set(categories);
    } catch {
      this.backendCategories.set([]);
    }
  }

  openAddResourceModal() {
    this.saveMessage.set('');
    this.showAddResourceModal.set(true);
  }

  closeAddResourceModal() {
    this.showAddResourceModal.set(false);
    this.selectedFile = null;
    this.selectedFileError = '';
  }

  async loadMyResources() {
    if (!this.authService.isAuthenticated()) {
      this.myResources.set([]);
      return;
    }

    this.isLoadingMyResources.set(true);
    this.editMessage.set('');
    try {
      const mine = await this.resourceService.getMyResources();
      this.myResources.set(mine);
    } catch {
      this.editMessage.set('Could not load your shared resources right now.');
    } finally {
      this.isLoadingMyResources.set(false);
    }
  }

  async loadResources(reset = false) {
    if (reset) {
      this.currentPage.set(0);
      this.resources.set([]);
      this.isLoading.set(true);
    } else {
      this.isLoadingMore.set(true);
    }

    this.errorMessage.set('');

    try {
      const page = this.currentPage();
      const response = await this.resourceService.getResources(page, PAGE_SIZE, this.currentFilters(), reset);
      const merged = reset ? response.content : [...this.resources(), ...response.content];
      this.resources.set(merged);
      this.hasNext.set(response.hasNext);
      this.currentPage.set(page + 1);
    } catch {
      this.errorMessage.set('Could not load community resources right now. Please try again.');
    } finally {
      this.isLoading.set(false);
      this.isLoadingMore.set(false);
    }
  }

  @HostListener('window:scroll')
  onWindowScroll() {
    if (!this.hasNext() || this.isLoading() || this.isLoadingMore() || this.showAddResourceModal()) {
      return;
    }

    const scrollTop = window.scrollY || document.documentElement.scrollTop || 0;
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    const fullHeight = document.documentElement.scrollHeight || document.body.scrollHeight || 0;
    const remaining = fullHeight - (scrollTop + viewportHeight);

    if (remaining > 220) {
      return;
    }

    if (this.scrollLoadDebounceRef) {
      return;
    }

    this.scrollLoadDebounceRef = setTimeout(() => {
      this.scrollLoadDebounceRef = null;
    }, 300);

    this.loadResources();
  }

  private scheduleFilterReload() {
    if (this.searchDebounceRef) {
      clearTimeout(this.searchDebounceRef);
    }

    this.searchDebounceRef = setTimeout(() => {
      this.loadResources(true);
    }, 250);
  }

  private currentFilters(): ResourceQueryFilters {
    return {
      query: this.searchQuery(),
      category: this.selectedCategoryFilter(),
      type: this.selectedTypeFilter()
    };
  }

  onModeChange(mode: 'link' | 'file') {
    this.contributionMode = mode;
    this.saveMessage.set('');
    this.selectedFile = null;
    this.selectedFileError = '';
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFileError = '';

    if (!file) {
      this.selectedFile = null;
      return;
    }

    const allowedTypes = [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'text/plain',
      'application/octet-stream'
    ];

    const validExtension = /\.(pdf|doc|docx|txt)$/i.test(file.name);
    const validType = allowedTypes.includes(file.type) || file.type === '';

    if (!validExtension || !validType) {
      this.selectedFile = null;
      this.selectedFileError = 'Only PDF, DOC, DOCX, and TXT files are allowed.';
      input.value = '';
      return;
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.selectedFile = null;
      this.selectedFileError = 'File size must be 10MB or less.';
      input.value = '';
      return;
    }

    this.selectedFile = file;
  }

  async addResource() {
    this.saveMessage.set('');

    if (!this.title.trim() || !this.category.trim()) {
      this.saveMessage.set('Title and category are required.');
      return;
    }

    if (this.contributionMode === 'link' && !this.url.trim()) {
      this.saveMessage.set('Resource URL is required for link contributions.');
      return;
    }

    if (this.contributionMode === 'file' && !this.selectedFile) {
      this.saveMessage.set('Please choose a file before uploading.');
      return;
    }

    this.isSaving.set(true);

    try {
      let created: CareerResource;

      if (this.contributionMode === 'file' && this.selectedFile) {
        created = await this.resourceService.uploadResourceFile({
          title: this.title,
          category: this.category,
          description: this.description,
          file: this.selectedFile
        });
      } else {
        created = await this.resourceService.createResource({
          title: this.title,
          url: this.url,
          category: this.category,
          description: this.description
        });
      }

      this.resources.set([created, ...this.resources()]);
      this.hasNext.set(true);
      this.title = '';
      this.url = '';
      this.category = '';
      this.description = '';
      this.selectedFile = null;
      this.saveMessage.set('Resource added. Thanks for contributing!');
      this.showAddResourceModal.set(false);
      await this.loadCategoryOptions(true);
      if (created.ownedByCurrentUser) {
        this.myResources.set([created, ...this.myResources()]);
      }
    } catch {
      this.saveMessage.set('Could not add the resource. Please verify your details and try again.');
    } finally {
      this.isSaving.set(false);
    }
  }

  async deleteResource(resource: CareerResource) {
    this.saveMessage.set('');
    this.editMessage.set('');
    try {
      await this.resourceService.deleteResource(resource.id);
      this.resources.set(this.resources().filter(item => item.id !== resource.id));
      this.myResources.set(this.myResources().filter(item => item.id !== resource.id));
      this.saveMessage.set('Resource removed successfully.');
      await this.loadCategoryOptions(true);
    } catch {
      this.saveMessage.set('Could not remove this resource right now.');
    }
  }

  startEditResource(resource: CareerResource) {
    this.editingResource.set(resource);
    this.editTitle = resource.title;
    this.editUrl = resource.resourceType === 'LINK' ? resource.url : '';
    this.editCategory = resource.category;
    this.editDescription = resource.description ?? '';
    this.editMessage.set('');
  }

  cancelEditResource() {
    this.editingResource.set(null);
    this.editMessage.set('');
  }

  async saveResourceEdit() {
    const target = this.editingResource();
    if (!target) {
      return;
    }

    if (!this.editTitle.trim() || !this.editCategory.trim()) {
      this.editMessage.set('Title and category are required.');
      return;
    }

    if (target.resourceType === 'LINK' && !this.editUrl.trim()) {
      this.editMessage.set('URL is required for link resources.');
      return;
    }

    this.isUpdating.set(true);
    this.editMessage.set('');

    try {
      const updated = await this.resourceService.updateResource(target.id, {
        title: this.editTitle,
        url: target.resourceType === 'LINK' ? this.editUrl : undefined,
        category: this.editCategory,
        description: this.editDescription
      });

      this.myResources.set(this.myResources().map(item => item.id === updated.id ? updated : item));
      this.resources.set(this.resources().map(item => item.id === updated.id ? updated : item));
      this.editingResource.set(null);
      this.saveMessage.set('Resource updated successfully.');
      await this.loadCategoryOptions(true);
    } catch {
      this.editMessage.set('Could not update this resource right now.');
    } finally {
      this.isUpdating.set(false);
    }
  }

  formatFileSize(size?: number) {
    if (!size) {
      return '';
    }

    if (size < 1024 * 1024) {
      return `${Math.ceil(size / 1024)} KB`;
    }

    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
}
