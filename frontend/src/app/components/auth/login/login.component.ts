import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../services/auth.service';
import { RouterLink } from '@angular/router';
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
export class LoginComponent {
  private readonly API = environment.apiBaseUrl;

  authService = inject(AuthService);
  email = '';
  password = '';

  isForgotPasswordMode = false;

  errorMessage = signal<string>('');
  successMessage = signal('');
  isLoading = signal<boolean>(false);

  async onSubmit() {
    this.errorMessage.set('');
    this.isLoading.set(true);

    try {
      await this.authService.login({
        email: this.email,
        password: this.password,
      });
    } catch (err: any) {
      this.isLoading.set(false);
      if (err.error && err.error.message) {
        this.errorMessage.set(err.error.message);
      } else {
        this.errorMessage.set('Login failed. Please try again.');
      }
    }
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

  clearMessages() {
    this.errorMessage.set('');
  }

  socialLogin(provider: string) {
    window.location.href = `${this.API}/oauth2/authorization/${provider}`;
  }
}
