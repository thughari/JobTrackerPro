import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CareerResource {
  id: string;
  title: string;
  url?: string;
  fileUrl?: string;
  category: string;
  description?: string;
  submittedByName: string;
  submittedByEmail: string;
  createdAt: string;
}

export interface CreateResourcePayload {
  title: string;
  category: string;
  url?: string;
  description?: string;
  file?: File | null;
}

@Injectable({ providedIn: 'root' })
export class ResourceService {
  private readonly API = environment.apiBaseUrl;
  private http = inject(HttpClient);
  private apiUrl = `${this.API}/api/resources`;

  async getResources() {
    return await firstValueFrom(this.http.get<CareerResource[]>(this.apiUrl));
  }

  async createResource(payload: CreateResourcePayload) {
    const formData = new FormData();
    formData.append('title', payload.title);
    formData.append('category', payload.category);

    if (payload.url) {
      formData.append('url', payload.url);
    }

    if (payload.description) {
      formData.append('description', payload.description);
    }

    if (payload.file) {
      formData.append('file', payload.file);
    }

    return await firstValueFrom(this.http.post<CareerResource>(this.apiUrl, formData));
  }

  async deleteResource(id: string) {
    return await firstValueFrom(this.http.delete(`${this.apiUrl}/${id}`));
  }
}
