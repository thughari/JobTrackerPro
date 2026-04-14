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
  location?: string;
  company?: string;
  eventDate?: string;
  listingType?: 'JOB' | 'WALK_IN' | 'EVENT' | 'RESOURCE';
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
  location?: string;
  company?: string;
  eventDate?: string;
  listingType?: string;
}

export interface UpdateResourcePayload {
  title: string;
  url?: string;
  category: string;
  description?: string;
  location?: string;
  company?: string;
  eventDate?: string;
  listingType?: string;
}

export interface ResourceQueryFilters {
  query?: string;
  category?: string;
  type?: 'all' | 'LINK' | 'FILE';
  location?: string;
  listingType?: string;
}

@Injectable({ providedIn: 'root' })
export class ResourceService {
  private readonly API = environment.apiBaseUrl;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = `${this.API}/api/resources`;
  private pageCache = new Map<string, CareerResourcePage>();
  private categoryCache = new Map<string, string[]>();

  async getResources(page: number, size: number, filters: ResourceQueryFilters = {}, forceRefresh = false) {
    const cacheScope = this.authService.currentUser()?.email ?? 'anonymous';
    const query = filters.query?.trim() ?? '';
    const category = filters.category?.trim() ?? '';
    const type = filters.type && filters.type !== 'all' ? filters.type : '';
    const location = filters.location?.trim() ?? '';
    const listingType = filters.listingType?.trim() ?? '';
    const key = `${cacheScope}:${page}:${size}:${query}:${category}:${type}:${location}:${listingType}`;

    if (!forceRefresh && this.pageCache.has(key)) {
      return this.pageCache.get(key)!;
    }

    const data = await firstValueFrom(
      this.http.get<CareerResourcePage>(this.apiUrl, {
        params: {
          page,
          size,
          ...(query ? { query } : {}),
          ...(category ? { category } : {}),
          ...(type ? { type } : {}),
          ...(location ? { location } : {}),
          ...(listingType ? { listingType } : {})
        }
      })
    );

    this.pageCache.set(key, data);
    return data;
  }

  invalidateCache() {
    this.pageCache.clear();
    this.categoryCache.clear();
  }


  async getResourceCategories(listingType?: string, forceRefresh = false) {
    const typeKey = listingType?.trim().toUpperCase() || 'ALL';
    if (!forceRefresh && this.categoryCache.has(typeKey)) {
      return this.categoryCache.get(typeKey)!;
    }

    const categories = await firstValueFrom(
      this.http.get<string[]>(`${this.apiUrl}/categories`, {
        params: listingType ? { listingType } : {}
      })
    );
    this.categoryCache.set(typeKey, categories);
    return categories;
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
    location?: string;
    company?: string;
    listingType?: string;
    eventDate?: string;
    file: File;
  }) {
    const formData = new FormData();
    formData.append('title', payload.title);
    formData.append('category', payload.category);
    if (payload.description?.trim()) {
      formData.append('description', payload.description.trim());
    }
    if (payload.location?.trim()) formData.append('location', payload.location.trim());
    if (payload.company?.trim()) formData.append('company', payload.company.trim());
    if (payload.listingType) formData.append('listingType', payload.listingType);
    if (payload.eventDate) formData.append('eventDate', payload.eventDate);
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

  async getMyResources() {
    return await firstValueFrom(this.http.get<CareerResource[]>(`${this.apiUrl}/mine`));
  }

  async updateResource(resourceId: string, payload: UpdateResourcePayload) {
    const updated = await firstValueFrom(
      this.http.put<CareerResource>(`${this.apiUrl}/${resourceId}`, payload)
    );

    this.invalidateCache();
    return updated;
  }
}
