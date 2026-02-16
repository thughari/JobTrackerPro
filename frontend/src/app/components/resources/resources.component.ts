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

    if (!this.title.trim() || !this.url.trim() || !this.category.trim()) {
      this.saveMessage.set('Title, URL, and category are required.');
      return;
    }

    this.isSaving.set(true);

    try {
      const created = await this.resourceService.createResource({
        title: this.title,
        url: this.url,
        category: this.category,
        description: this.description
      });

      this.resources.set([created, ...this.resources()]);
      this.title = '';
      this.url = '';
      this.category = '';
      this.description = '';
      this.saveMessage.set('Resource added. Thanks for contributing!');
    } catch {
      this.saveMessage.set('Could not add the resource. Please check the link and try again.');
    } finally {
      this.isSaving.set(false);
    }
  }
}
