import { Injectable, signal, inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { JobService } from './job.service';
import { environment } from '../../environments/environment';

export interface UserProfile {
  name: string;
  email: string;
  imageUrl?: string;
  provider: string;
  hasPassword: boolean;
  gmailConnected: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = environment.apiBaseUrl;

  private http = inject(HttpClient);
  private router = inject(Router);

  private injector = inject(Injector);

  private apiUrl = `${this.API}/api/auth`;
  private refreshInFlight: Promise<boolean> | null = null;

  userProfile = signal<UserProfile | null>(null);
  currentUser = signal<{ email: string } | null>(this.decodeToken());

  constructor() {}

  async fetchUserProfile() {
    try {
      const user = await firstValueFrom(
        this.http.get<UserProfile>(`${this.apiUrl}/me`)
      );
      this.userProfile.set(user);
    } catch (e) {
      const status = (e as HttpErrorResponse)?.status;

      if (status === 401 && this.isAuthenticated()) {
        const refreshed = await this.refreshToken();
        if (refreshed) {
          try {
            const user = await firstValueFrom(this.http.get<UserProfile>(`${this.apiUrl}/me`));
            this.userProfile.set(user);
            return;
          } catch {
            // fall through and clear session below
          }
        }

        this.clearClientSession();
        this.router.navigate(['/']);
        return;
      }

      if (status !== 0) {
        console.warn('Profile bootstrap failed without auth error; keeping session.', e);
      }
    }
  }

  isAuthenticated() {
    return !!localStorage.getItem('token');
  }

  getAccessToken() {
    return localStorage.getItem('token');
  }

  async login(credentials: any) {
    const res: any = await firstValueFrom(
      this.http.post(`${this.apiUrl}/login`, credentials, { withCredentials: true })
    );
    this.handleToken(res.token);
  }

  async signup(data: any) {
    const res: any = await firstValueFrom(
      this.http.post(`${this.apiUrl}/signup`, data, { withCredentials: true })
    );
    this.handleToken(res.token);
  }

  async refreshToken(): Promise<boolean> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    this.refreshInFlight = (async () => {
      try {
        const res: any = await firstValueFrom(
          this.http.post(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
        );
        this.setAccessToken(res.token);
        return true;
      } catch {
        return false;
      } finally {
        this.refreshInFlight = null;
      }
    })();

    return this.refreshInFlight;
  }

  handleToken(token: string) {
    this.setAccessToken(token);
    this.router.navigate(['/app/dashboard']);
    this.fetchUserProfile();
  }

  setAccessToken(token: string) {
    localStorage.setItem('token', token);
    this.currentUser.set(this.decodeToken());
  }

  logout() {
    this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true }).subscribe({
      error: () => {
        // no-op: continue client logout even if backend logout fails
      }
    });

    this.clearClientSession();
    this.router.navigate(['/']);
  }

  private clearClientSession() {
    localStorage.removeItem('token');
    this.userProfile.set(null);
    this.currentUser.set(null);

    const jobService = this.injector.get(JobService);
    jobService.clearState();
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
    } catch {
      return {};
    }
  }

  connectGmail(code: string) {
    // We send the 'code' in the body. The backend swaps it for a Refresh Token.
    return this.http.post(`${this.API}/api/integrations/gmail/connect`, 
      { code }, 
      { responseType: 'text' }
    );
  }
}
