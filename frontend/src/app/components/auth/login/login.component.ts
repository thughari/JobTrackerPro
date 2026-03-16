import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { LogoComponent } from '../../ui/logo/logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, FormsModule, CommonModule, LogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements OnInit {
  private readonly API = environment.apiBaseUrl;

  authService = inject(AuthService);
  router = inject(Router);
  email = '';
  password = '';

  isForgotPasswordMode = false;

  errorMessage = signal<string>('');
  successMessage = signal('');
  isLoading = signal<boolean>(false);
  showPendingDeletionPrompt = signal<boolean>(false);
  pendingDeletionDays = signal<number>(0);

  async onSubmit() {
    this.errorMessage.set('');
    this.isLoading.set(true);

    try {
      const profile = await this.authService.login({
        email: this.email,
        password: this.password,
      });

      this.isLoading.set(false);

      if (profile?.pendingDeletion) {
        this.pendingDeletionDays.set(profile.daysUntilDeletion ?? 0);
        this.showPendingDeletionPrompt.set(true);
        return;
      }

      this.router.navigate(['/app/dashboard']);
    } catch (err: any) {
      this.isLoading.set(false);
      if (err.error && err.error.message) {
        this.errorMessage.set(err.error.message);
      } else {
        this.errorMessage.set('Login failed. Please try again.');
      }
    }
  }

  async cancelDeletionAndContinue() {
    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      await firstValueFrom(this.authService.cancelDeletion());
      await this.authService.fetchUserProfile();
      this.showPendingDeletionPrompt.set(false);
      this.router.navigate(['/app/dashboard']);
    } catch (err: any) {
      this.errorMessage.set('Could not cancel deletion. Please try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  declineDeletionAndLogout() {
    this.showPendingDeletionPrompt.set(false);
    this.authService.logout();
  }

  toggleMode() {
    this.isForgotPasswordMode = !this.isForgotPasswordMode;
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  async onForgotPassword() {
    if (!this.email) {
      this.errorMessage.set('Please enter your email.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');

    try {
      const res: any = await this.authService.forgotPassword(this.email);
      console.log(res);
      this.successMessage.set(res || 'Reset link sent! Check your inbox.');
    } catch (err: any) {
      this.errorMessage.set('Failed to send email.');
    } finally {
      this.isLoading.set(false);
    }
  }

  ngOnInit() {
    const profile = this.authService.userProfile();
    if (profile?.pendingDeletion) {
      this.pendingDeletionDays.set(profile.daysUntilDeletion ?? 0);
      this.showPendingDeletionPrompt.set(true);
    }
  }

  clearMessages() {
    this.errorMessage.set('');
  }

  socialLogin(provider: string) {
    window.location.href = `${this.API}/oauth2/authorization/${provider}`;
  }
}
