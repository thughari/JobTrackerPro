import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink, Router } from '@angular/router'; // Added Router
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service'; // Added AuthService

export interface AuthResponse {
  token: string;
}

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verify.component.html',
  styleUrl: './verify.component.css'
})
export class VerifyComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  status = signal<'loading' | 'success' | 'error'>('loading');
  errorMessage = signal<string>('');

  async ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    
    if (!token) {
      this.status.set('error');
      this.errorMessage.set('Invalid or missing verification link.');
      return;
    }

    try {
      const response = await firstValueFrom(
        this.http.get<AuthResponse>(`${environment.apiBaseUrl}/api/auth/verify-email?token=${token}`)
      );

      this.authService.setAccessToken(response.token);
      
      this.status.set('success');
      
      setTimeout(() => {
        this.router.navigate(['/app/dashboard']);
        this.authService.fetchUserProfile();
      }, 2000);

    } catch (err: any) {
      this.status.set('error');
      this.errorMessage.set(err.error?.message || err.error || 'Verification failed. The link may be expired.');
    }
  }
}