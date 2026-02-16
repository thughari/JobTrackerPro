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
  readonly deletingResourceId = signal<string | null>(null);

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

    if (!file) {
      this.selectedFile = null;
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.saveMessage.set('File is too large. Max size is 10MB.');
      this.selectedFile = null;
      input.value = '';
      return;
    }

    this.selectedFile = file;
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
    } catch {
      this.saveMessage.set('Could not add the resource. Please check inputs and try again.');
    } finally {
      this.isSaving.set(false);
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
}
