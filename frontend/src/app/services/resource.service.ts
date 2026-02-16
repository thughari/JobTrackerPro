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
  private http = inject(HttpClient);
  private apiUrl = `${this.API}/api/resources`;

  async getResources() {
    return await firstValueFrom(this.http.get<CareerResource[]>(this.apiUrl));
  }

  async createResource(payload: CreateResourcePayload) {
    return await firstValueFrom(this.http.post<CareerResource>(this.apiUrl, payload));
  }
}
