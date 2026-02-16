import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface CareerResource {
  id: string;
  title: string;
  url: string;
  category: string;
  description?: string;
  resourceType: 'LINK' | 'FILE';
  originalFileName?: string;
  fileSizeBytes?: number;
  ownedByCurrentUser: boolean;
  submittedByName: string;
  createdAt: string;
}

export interface CareerResourcePage {
  content: CareerResource[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface CreateResourcePayload {
  title: string;
  url: string;
  category: string;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class ResourceService {
  private readonly API = environment.apiBaseUrl;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = `${this.API}/api/resources`;
  private pageCache = new Map<string, CareerResourcePage>();

  async getResources(page: number, size: number, forceRefresh = false) {
    const cacheScope = this.authService.currentUser()?.email ?? 'anonymous';
    const key = `${cacheScope}:${page}:${size}`;

    if (!forceRefresh && this.pageCache.has(key)) {
      return this.pageCache.get(key)!;
    }

    const data = await firstValueFrom(
      this.http.get<CareerResourcePage>(this.apiUrl, {
        params: {
          page,
          size
        }
      })
    );

    this.pageCache.set(key, data);
    return data;
  }

  invalidateCache() {
    this.pageCache.clear();
  }

  async createResource(payload: CreateResourcePayload) {
    const created = await firstValueFrom(this.http.post<CareerResource>(this.apiUrl, payload));
    this.invalidateCache();
    return created;
  }

  async uploadResourceFile(payload: {
    title: string;
    category: string;
    description?: string;
    file: File;
  }) {
    const formData = new FormData();
    formData.append('title', payload.title);
    formData.append('category', payload.category);
    if (payload.description?.trim()) {
      formData.append('description', payload.description.trim());
    }
    formData.append('file', payload.file);

    const created = await firstValueFrom(
      this.http.post<CareerResource>(`${this.apiUrl}/upload`, formData)
    );

    this.invalidateCache();
    return created;
  }

  async deleteResource(resourceId: string) {
    await firstValueFrom(this.http.delete<void>(`${this.apiUrl}/${resourceId}`));
    this.invalidateCache();
  }
}
