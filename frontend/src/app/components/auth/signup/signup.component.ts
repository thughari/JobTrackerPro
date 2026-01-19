import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { LogoComponent } from '../../ui/logo/logo.component';

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

  signUpUser: SignUpUser = {
    email: '',
    password: '',
    name: '',
  };

  errorMessage = signal<string>('');
  isLoading = signal<boolean>(false);

  showPassword = signal(false);
  passwordStrength = signal(0);

  onPasswordInput() {
    let score = 0;
    const p = this.signUpUser.password;

    if (!p) {
      this.passwordStrength.set(0);
      return;
    }

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
      this.errorMessage.set('Please fill in all fields');
      return;
    }

    if (this.signUpUser.password.length < 6) {
      this.errorMessage.set('Password must be at least 6 characters');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      await this.authService.signup(this.signUpUser);
    } catch (err: any) {
      this.isLoading.set(false);
      if (err.error && err.error.message) {
        this.errorMessage.set(err.error.message);
      } else {
        this.errorMessage.set('Signup failed. Please try again.');
      }
    }
  }

  clearMessages() {
    this.errorMessage.set('');
  }

  socialSignUp(provider: string) {
    window.location.href = `${this.API}/oauth2/authorization/${provider}`;
  }
}