import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { LogoComponent } from '../../ui/logo/logo.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink, LogoComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.css'
})
export class ResetPasswordComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  token = '';
  password = '';
  confirmPassword = '';
  
  message = signal('');
  isError = signal(false);
  isLoading = signal(false);

  showPassword = signal(false);
  showConfirmPassword = signal(false);
  passwordStrength = signal(0);
  passwordMismatch = false;

  ngOnInit() {
    if (this.password !== this.confirmPassword) {
      this.passwordMismatch = true;
      return;
    }
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.isError.set(true);
      this.message.set('Invalid link. Please try "Forgot Password" again.');
    }
  }

  checkMismatch() {
    if (!this.confirmPassword) {
      this.passwordMismatch = false;
      return;
    }
    this.passwordMismatch = this.password !== this.confirmPassword;
  }

  onPasswordInput() {
    let score = 0;
    const p = this.password;
    
    if (!p) {
      this.passwordStrength.set(0);
    } else {
      if (p.length >= 8) score++;
      if (/[A-Z]/.test(p)) score++;
      if (/[0-9]/.test(p)) score++;
      if (/[^A-Za-z0-9]/.test(p)) score++;
      this.passwordStrength.set(score);
    }

    if (this.confirmPassword) {
      this.checkMismatch();
    }
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
    if (this.password !== this.confirmPassword) {
      this.isError.set(true);
      this.message.set('Passwords do not match');
      return;
    }
    if (this.password.length < 6) {
      this.isError.set(true);
      this.message.set('Password must be at least 6 characters long');
      return;
    }

    this.isLoading.set(true);
    try {
      await this.authService.resetPassword(this.token, this.password);
      this.isError.set(false);
      this.message.set('Password reset successfully! Redirecting...');
      setTimeout(() => this.router.navigate(['/login']), 2000);
    } catch (err: any) {
      this.isError.set(true);
      this.message.set(err.error || 'Failed to reset password.');
    } finally {
      this.isLoading.set(false);
    }
  }
}