import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { LogoComponent } from '../../ui/logo/logo.component';
import { firstValueFrom } from 'rxjs';

export interface SignUpUser {
  email: string;
  password: string;
  name: string;
}

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, CommonModule, LogoComponent],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.css',
})
export class SignupComponent {
  private readonly API = environment.apiBaseUrl;
  authService = inject(AuthService);

  signUpUser: SignUpUser = { email: '', password: '', name: '' };

  // --- UI SIGNALS ---
  isLoading = signal(false);
  signupSuccess = signal(false);
  isResending = signal(false);
  resendCooldown = signal(0);
  
  errorMessage = signal('');
  successMessage = signal('');
  private messageTimeout: any;

  showPassword = signal(false);
  passwordStrength = signal(0);

  // --- PASSWORD LOGIC ---
  onPasswordInput() {
    let score = 0;
    const p = this.signUpUser.password;
    if (!p) { this.passwordStrength.set(0); return; }
    if (p.length >= 8) score++;
    if (/[A-Z]/.test(p)) score++;
    if (/[0-9]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;
    this.passwordStrength.set(score);
  }

  getStrengthColor(): string {
    const s = this.passwordStrength();
    if (s <= 1) return 'bg-red-500';
    if (s === 2) return 'bg-orange-500';
    if (s === 3) return 'bg-yellow-500';
    return 'bg-green-500';
  }

  getStrengthLabel(): string {
    const s = this.passwordStrength();
    if (s === 0) return '';
    if (s <= 2) return 'Weak';
    if (s === 3) return 'Medium';
    return 'Strong';
  }

  async onSubmit() {
    if (!this.signUpUser.name || !this.signUpUser.email || !this.signUpUser.password) {
      this.showMessage('error', 'Please fill in all fields');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      const response: any = await firstValueFrom(this.authService.signup(this.signUpUser));
      this.successMessage.set(response.message || 'Check your email to verify your account.');
      this.signupSuccess.set(true);
    } catch (err: any) {
      const msg = err.error?.message || err.error || 'Signup failed.';
      this.showMessage('error', msg);
    } finally {
      this.isLoading.set(false);
    }
  }

  async resendEmail() {
    if (this.resendCooldown() > 0 || this.isResending()) return;

    this.isResending.set(true);
    try {
      await firstValueFrom(this.authService.resendVerificationEmail(this.signUpUser.email));
      this.showMessage('success', 'New verification link sent!');
      
      this.resendCooldown.set(60);
      const interval = setInterval(() => {
        this.resendCooldown.update(v => v - 1);
        if (this.resendCooldown() <= 0) clearInterval(interval);
      }, 1000);
    } catch (err) {
      this.showMessage('error', 'Failed to resend. Please try again later.');
    } finally {
      this.isResending.set(false);
    }
  }

  showMessage(type: 'success' | 'error', message: string) {
    this.clearMessages();
    if (type === 'success') this.successMessage.set(message);
    else this.errorMessage.set(message);

    this.messageTimeout = setTimeout(() => this.clearMessages(), 5000);
  }

  clearMessages() {
    this.errorMessage.set('');
    if (!this.signupSuccess()) this.successMessage.set('');
    if (this.messageTimeout) clearTimeout(this.messageTimeout);
  }

  socialSignUp(provider: string) {
    window.location.href = `${this.API}/oauth2/authorization/${provider}`;
  }
}