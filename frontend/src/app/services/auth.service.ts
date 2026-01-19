import { Injectable, signal, inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { JobService } from './job.service';
import { environment } from '../../environments/environment';

export interface UserProfile {
  name: string;
  email: string;
  imageUrl?: string;
  provider: string;
  hasPassword: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = environment.apiBaseUrl;

  private http = inject(HttpClient);
  private router = inject(Router);

  private injector = inject(Injector);

  private apiUrl = `${this.API}/api/auth`;

  userProfile = signal<UserProfile | null>(null);
  currentUser = signal<{ email: string } | null>(this.decodeToken());

  constructor() {
    if (this.isAuthenticated()) {
      this.fetchUserProfile();
    }
  }

  async fetchUserProfile() {
    try {
      const user = await firstValueFrom(
        this.http.get<UserProfile>(`${this.apiUrl}/me`)
      );
      this.userProfile.set(user);
    } catch (e) {
      this.logout();
    }
  }

  isAuthenticated() {
    return !!localStorage.getItem('token');
  }

  async login(credentials: any) {
    const res: any = await firstValueFrom(
      this.http.post(`${this.apiUrl}/login`, credentials)
    );
    this.handleToken(res.token);
  }

  async signup(data: any) {
    const res: any = await firstValueFrom(
      this.http.post(`${this.apiUrl}/signup`, data)
    );
    this.handleToken(res.token);
  }

  handleToken(token: string) {
    localStorage.setItem('token', token);
    this.router.navigate(['/app/dashboard']);
    this.fetchUserProfile();
  }

  logout() {
    localStorage.removeItem('token');
    this.userProfile.set(null);
    this.currentUser.set(null);

    const jobService = this.injector.get(JobService);
    jobService.clearState();

    this.router.navigate(['/']);
  }

  async updateProfile(name: string, imageUrl: string, file: File | null) {
    const formData = new FormData();
    formData.append('name', name);

    if (imageUrl) {
      formData.append('imageUrl', imageUrl);
    }

    if (file) {
      formData.append('file', file);
    }

    await firstValueFrom(this.http.put(`${this.apiUrl}/profile`, formData));

    this.fetchUserProfile();
  }

  async changePassword(data: { currentPassword: string; newPassword: string }) {
    return await firstValueFrom(
      this.http.put(`${this.apiUrl}/password`, data, { responseType: 'text' })
    );
  }

  async forgotPassword(email: string) {
    const formData = new FormData();
    formData.append('email', email);

    return await firstValueFrom(
      this.http.post(`${this.apiUrl}/forgot-password`, formData, { 
        responseType: 'text'
      })
    );
  }

  async resetPassword(token: string, newPassword: string) {
    return await firstValueFrom(
      this.http.post(`${this.apiUrl}/reset-password`, 
        { token, newPassword }, 
        { responseType: 'text' }
      )
    );
  }

  private decodeToken() {
    const token = localStorage.getItem('token');
    return token ? { email: this.parseJwt(token).sub } : null;
  }

  private parseJwt(token: string) {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        window
          .atob(base64)
          .split('')
          .map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
          })
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (e) {
      return {};
    }
  }
}
