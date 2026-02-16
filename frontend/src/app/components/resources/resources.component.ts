import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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

  readonly resources = signal<CareerResource[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal('');
  readonly saveMessage = signal('');
  readonly isSaving = signal(false);

  title = '';
  url = '';
  category = '';
  description = '';
  selectedFile: File | null = null;

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
    this.loadResources();
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

    if (!this.selectedFile && !this.url.trim()) {
      this.saveMessage.set('Add a URL or upload a file.');
      return;
    }

    this.isSaving.set(true);

    try {
      let created: CareerResource;
      if (this.selectedFile) {
        if (this.selectedFile.size > 5 * 1024 * 1024) {
          this.saveMessage.set('File must be 5MB or smaller.');
          return;
        }
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
      this.resetForm();
      this.saveMessage.set('Resource added. Thanks for contributing!');
    } catch {
      this.saveMessage.set('Could not add the resource. Please check details and try again.');
    } finally {
      this.isSaving.set(false);
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile = file;
    if (file) {
      this.url = '';
    }
  }

  async removeResource(resource: CareerResource) {
    if (!this.canDelete(resource)) {
      return;
    }

    try {
      await this.resourceService.deleteResource(resource.id);
      this.resources.set(this.resources().filter((item) => item.id !== resource.id));
    } catch {
      this.saveMessage.set('Could not delete this resource. Please try again.');
    }
  }

  canDelete(resource: CareerResource) {
    return (
      this.authService.isAuthenticated() &&
      this.authService.userProfile()?.email?.toLowerCase() ===
        resource.submittedByEmail?.toLowerCase()
    );
  }

  formatBytes(bytes?: number) {
    if (!bytes) {
      return '';
    }
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    return `${(kb / 1024).toFixed(1)} MB`;
  }

  private resetForm() {
    this.title = '';
    this.url = '';
    this.category = '';
    this.description = '';
    this.selectedFile = null;
  }
}
