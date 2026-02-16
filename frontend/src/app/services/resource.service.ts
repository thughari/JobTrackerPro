import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CareerResource {
  id: string;
  title: string;
  url: string;
  category: string;
  description?: string;
  submittedByName: string;
  createdAt: string;
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
  private readonly cacheKey = 'career-resources-cache-v1';
  private readonly cacheTtlMs = 5 * 60 * 1000;
  private http = inject(HttpClient);
  private apiUrl = `${this.API}/api/resources`;
  private memoryCache: CareerResource[] | null = null;
  private memoryCacheUpdatedAt = 0;
  private inFlightRequest: Promise<CareerResource[]> | null = null;

  async getResources(options?: { forceRefresh?: boolean }) {
    const forceRefresh = options?.forceRefresh ?? false;

    if (!forceRefresh && this.hasFreshMemoryCache()) {
      return [...(this.memoryCache ?? [])];
    }

    if (!forceRefresh && this.restoreCacheFromSessionStorage()) {
      return [...(this.memoryCache ?? [])];
    }

    if (this.inFlightRequest) {
      return await this.inFlightRequest;
    }

    this.inFlightRequest = firstValueFrom(this.http.get<CareerResource[]>(this.apiUrl))
      .then((resources) => {
        this.updateCache(resources);
        return [...resources];
      })
      .finally(() => {
        this.inFlightRequest = null;
      });

    return await this.inFlightRequest;
  }

  async createResource(payload: CreateResourcePayload) {
    const created = await firstValueFrom(this.http.post<CareerResource>(this.apiUrl, payload));
    const cachedResources = this.memoryCache ?? [];
    this.updateCache([created, ...cachedResources]);
    return created;
  }

  private hasFreshMemoryCache() {
    if (!this.memoryCache) {
      return false;
    }

    return Date.now() - this.memoryCacheUpdatedAt < this.cacheTtlMs;
  }

  private restoreCacheFromSessionStorage() {
    if (typeof window === 'undefined') {
      return false;
    }

    const raw = window.sessionStorage.getItem(this.cacheKey);
    if (!raw) {
      return false;
    }

    let parsed: { data: CareerResource[]; updatedAt: number } | null = null;

    try {
      parsed = JSON.parse(raw) as { data: CareerResource[]; updatedAt: number };
    } catch {
      window.sessionStorage.removeItem(this.cacheKey);
      return false;
    }

    if (!parsed) {
      return false;
    }
    const isFresh = Date.now() - parsed.updatedAt < this.cacheTtlMs;

    if (!isFresh || !Array.isArray(parsed.data)) {
      window.sessionStorage.removeItem(this.cacheKey);
      return false;
    }

    this.memoryCache = parsed.data;
    this.memoryCacheUpdatedAt = parsed.updatedAt;
    return true;
  }

  private updateCache(resources: CareerResource[]) {
    this.memoryCache = resources;
    this.memoryCacheUpdatedAt = Date.now();

    if (typeof window === 'undefined') {
      return;
    }

    window.sessionStorage.setItem(this.cacheKey, JSON.stringify({
      data: resources,
      updatedAt: this.memoryCacheUpdatedAt
    }));
  }
}
