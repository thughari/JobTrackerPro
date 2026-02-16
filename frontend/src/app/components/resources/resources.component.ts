import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CareerResource, ResourceService } from '../../services/resource.service';
import { LogoComponent } from '../ui/logo/logo.component';

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
  readonly errorMessage = signal('');
  readonly saveMessage = signal('');
  readonly isSaving = signal(false);
  readonly deletingResourceId = signal<string | null>(null);
  readonly updatingResourceId = signal<string | null>(null);

  title = '';
  url = '';
  category = '';
  description = '';
  selectedFile: File | null = null;

  readonly searchTerm = signal('');
  readonly selectedCategoryFilter = signal('All');
  readonly showAddForm = signal(false);

  editingResourceId: string | null = null;
  editTitle = '';
  editUrl = '';
  editCategory = '';
  editDescription = '';
  editFile: File | null = null;
  removeExistingFile = false;

  readonly categoryOptions = computed(() => {
    const categories = new Set<string>();
    for (const item of this.resources()) {
      categories.add(item.category?.trim() || 'General');
    }
    return ['All', ...Array.from(categories).sort((a, b) => a.localeCompare(b))];
  });

  readonly filteredResources = computed(() => {
    const search = this.searchTerm().trim().toLowerCase();

    return this.resources().filter((resource) => {
      const selectedCategory = this.selectedCategoryFilter();
      const matchesCategory = selectedCategory === 'All' || resource.category === selectedCategory;

      if (!matchesCategory) {
        return false;
      }

      if (!search) {
        return true;
      }

      const haystack = [
        resource.title,
        resource.category,
        resource.description || '',
        resource.submittedByName
      ].join(' ').toLowerCase();

      return haystack.includes(search);
    });
  });

  readonly groupedResources = computed(() => {
    const grouped = new Map<string, CareerResource[]>();

    for (const resource of this.filteredResources()) {
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


  onSearchTermChange(value: string) {
    this.searchTerm.set(value);
  }

  onCategoryFilterChange(value: string) {
    this.selectedCategoryFilter.set(value);
  }

  toggleAddForm() {
    this.showAddForm.update((value) => !value);
  }

  constructor() {
    this.loadResources();
  }

  showStandaloneNav() {
    return !this.router.url.startsWith('/app');
  }

  isOwner(resource: CareerResource) {
    const userEmail = this.authService.currentUser()?.email?.toLowerCase();
    return !!userEmail && userEmail === resource.submittedByEmail.toLowerCase();
  }

  getPrimaryLink(resource: CareerResource) {
    return resource.url || resource.fileUrl || '#';
  }

  getLinkLabel(resource: CareerResource) {
    return resource.fileUrl && !resource.url ? 'Open file' : 'Open link';
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] || null;

    if (!this.isValidUpload(file)) {
      input.value = '';
      this.selectedFile = null;
      return;
    }

    this.selectedFile = file;
  }

  onEditFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] || null;

    if (!this.isValidUpload(file)) {
      input.value = '';
      this.editFile = null;
      return;
    }

    this.editFile = file;
  }

  async loadResources() {
    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      const data = await this.resourceService.getResources();
      this.resources.set(data);
    } catch {
      this.errorMessage.set('Could not load community resources right now. Please try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async addResource() {
    this.saveMessage.set('');

    if (!this.title.trim() || !this.category.trim()) {
      this.saveMessage.set('Title and category are required.');
      return;
    }

    if (!this.url.trim() && !this.selectedFile) {
      this.saveMessage.set('Provide either a URL or upload a file.');
      return;
    }

    this.isSaving.set(true);

    try {
      const created = await this.resourceService.createResource({
        title: this.title,
        url: this.url,
        category: this.category,
        description: this.description,
        file: this.selectedFile
      });

      this.resources.set([created, ...this.resources()]);
      this.title = '';
      this.url = '';
      this.category = '';
      this.description = '';
      this.selectedFile = null;

      const fileInput = document.getElementById('resource-file-input') as HTMLInputElement | null;
      if (fileInput) {
        fileInput.value = '';
      }

      this.saveMessage.set('Resource added. Thanks for contributing!');
      this.showAddForm.set(false);
    } catch {
      this.saveMessage.set('Could not add the resource. Please check inputs and try again.');
    } finally {
      this.isSaving.set(false);
    }
  }

  startEditing(resource: CareerResource) {
    this.editingResourceId = resource.id;
    this.editTitle = resource.title;
    this.editUrl = resource.url || '';
    this.editCategory = resource.category;
    this.editDescription = resource.description || '';
    this.editFile = null;
    this.removeExistingFile = false;
    this.saveMessage.set('');
  }

  cancelEditing() {
    this.editingResourceId = null;
    this.editTitle = '';
    this.editUrl = '';
    this.editCategory = '';
    this.editDescription = '';
    this.editFile = null;
    this.removeExistingFile = false;
  }

  async saveEdit(resource: CareerResource) {
    if (!this.isOwner(resource)) {
      return;
    }

    if (!this.editTitle.trim() || !this.editCategory.trim()) {
      this.saveMessage.set('Title and category are required for update.');
      return;
    }

    const effectiveHasFile = !!this.editFile || (!!resource.fileUrl && !this.removeExistingFile);
    if (!this.editUrl.trim() && !effectiveHasFile) {
      this.saveMessage.set('Keep URL or file. A resource needs at least one.');
      return;
    }

    this.updatingResourceId.set(resource.id);
    this.saveMessage.set('');

    try {
      const updated = await this.resourceService.updateResource({
        id: resource.id,
        title: this.editTitle,
        url: this.editUrl,
        category: this.editCategory,
        description: this.editDescription,
        file: this.editFile,
        removeFile: this.removeExistingFile
      });

      this.resources.set(
        this.resources().map((item) => (item.id === updated.id ? updated : item))
      );
      this.cancelEditing();
      this.saveMessage.set('Resource updated successfully.');
    } catch {
      this.saveMessage.set('Could not update resource. Please try again.');
    } finally {
      this.updatingResourceId.set(null);
    }
  }

  async deleteResource(resource: CareerResource) {
    if (!this.isOwner(resource)) {
      return;
    }

    this.deletingResourceId.set(resource.id);
    this.saveMessage.set('');

    try {
      await this.resourceService.deleteResource(resource.id);
      this.resources.set(this.resources().filter((item) => item.id !== resource.id));
      this.saveMessage.set('Resource removed successfully.');
    } catch {
      this.saveMessage.set('Could not remove resource. Please try again.');
    } finally {
      this.deletingResourceId.set(null);
    }
  }

  private isValidUpload(file: File | null) {
    if (!file) {
      return true;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.saveMessage.set('File is too large. Max size is 10MB.');
      return false;
    }

    return true;
  }
}
