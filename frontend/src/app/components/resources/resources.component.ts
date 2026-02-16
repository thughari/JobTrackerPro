import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
  styleUrl: './resources.component.css'
})
export class ResourcesComponent {
  authService = inject(AuthService);
  private resourceService = inject(ResourceService);

  readonly resources = signal<CareerResource[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hasNext = signal(false);
  readonly currentPage = signal(0);
  readonly errorMessage = signal('');
  readonly saveMessage = signal('');
  readonly isSaving = signal(false);

  title = '';
  url = '';
  category = '';
  description = '';
  contributionMode: 'link' | 'file' = 'link';
  selectedFile: File | null = null;
  selectedFileError = '';

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
    this.loadResources(true);
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
      const response = await this.resourceService.getResources(page, PAGE_SIZE, reset);
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
    } catch {
      this.saveMessage.set('Could not add the resource. Please verify your details and try again.');
    } finally {
      this.isSaving.set(false);
    }
  }

  async deleteResource(resource: CareerResource) {
    this.saveMessage.set('');
    try {
      await this.resourceService.deleteResource(resource.id);
      this.resources.set(this.resources().filter(item => item.id !== resource.id));
      this.saveMessage.set('Resource removed successfully.');
    } catch {
      this.saveMessage.set('Could not remove this resource right now.');
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
