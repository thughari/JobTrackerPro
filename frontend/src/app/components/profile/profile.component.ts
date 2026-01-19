import { Component, effect, inject, signal } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  private readonly API = environment.apiBaseUrl;

  authService = inject(AuthService);

  profileForm = signal({ name: '', imageUrl: '' });
  passwordForm = signal({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  isEditingProfile = signal(false);
  isEditingPassword = signal(false);

  userHasPassword = signal(false);

  isUploading = signal(false);
  imageTimestamp = signal(Date.now());
  showImageModal = signal(false);
  tempImageUrl = signal('');

  successMessage = signal('');
  errorMessage = signal('');

  pendingFile: File | null = null;
  localPreviewUrl = signal<string | null>(null);

  showHelpModal = signal(false);

  readonly atsFilterQuery = `from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net)`;

  readonly subjectFilterQuery = `subject:("Application Received" OR "Application Confirmation" OR "Thank you for applying" OR "Interview Invitation" OR "Interview Request" OR "Coding Challenge" OR "Assessment Invitation" OR "Status of your application")`;

  private messageTimeout: any;

  userProfile = this.authService.userProfile();

  constructor() {
    effect(
      () => {
        const user = this.authService.userProfile();
        if (user) {
          this.profileForm.set({
            name: user.name || '',
            imageUrl: user.imageUrl || '',
          });
          this.pendingFile = null;
          this.localPreviewUrl.set(null);
          this.userHasPassword.set(user.hasPassword);
        }
      },
      { allowSignalWrites: true }
    );
  }

  showMessage(type: 'success' | 'error', message: string) {
    this.clearMessages();

    if (type === 'success') {
      this.successMessage.set(message);
    } else {
      this.errorMessage.set(message);
    }

    this.messageTimeout = setTimeout(() => {
      this.clearMessages();
    }, 5000);
  }

  clearMessages() {
    this.successMessage.set('');
    this.errorMessage.set('');
    if (this.messageTimeout) {
      clearTimeout(this.messageTimeout);
    }
  }

  toggleEditProfile() {
    this.isEditingProfile.update((v) => !v);
    if (!this.isEditingProfile()) {
      this.resetProfileForm(this.authService.userProfile());
    }
    this.clearMessages();
  }

  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        this.showMessage('error', 'Image size must be less than 5MB');
        return;
      }

      this.pendingFile = file;
      this.closeImageModal();

      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.localPreviewUrl.set(e.target.result);
      };
      reader.readAsDataURL(file);
    }
  }

  async onUpdateProfile() {
    this.clearMessages();
    this.isUploading.set(true);
    try {
      await this.authService.updateProfile(
        this.profileForm().name,
        this.profileForm().imageUrl,
        this.pendingFile
      );

      this.showMessage('success', 'Profile updated successfully!');
      this.isEditingProfile.set(false);

      this.pendingFile = null;
      this.localPreviewUrl.set(null);
      this.imageTimestamp.set(Date.now());
    } catch (err: any) {
      if (err.error && err.error.message) {
        this.showMessage('error', err.error.message);
      } else {
        this.showMessage(
          'error',
          'Failed to update profile. Please try again.'
        );
      }
    } finally {
      this.isUploading.set(false);
    }
  }

  toggleEditPassword() {
    this.isEditingPassword.update((v) => !v);
    this.passwordForm.set({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    this.clearMessages();
  }

  async onChangePassword() {
    this.clearMessages();
    const { currentPassword, newPassword, confirmPassword } =
      this.passwordForm();

    if (newPassword.length < 6) {
      this.showMessage('error', 'Password must be at least 6 characters.');
      return;
    }

    if (newPassword !== confirmPassword) {
      this.showMessage('error', 'New passwords do not match.');
      return;
    }

    try {
      await this.authService.changePassword({ currentPassword, newPassword });
      this.showMessage('success', 'Password updated successfully.');
      this.isEditingPassword.set(false);
      this.passwordForm.set({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      });
      this.authService.fetchUserProfile();
    } catch (err: any) {
      if (err.error && err.error.message) {
        this.showMessage('error', err.error.message);
      } else {
        this.showMessage(
          'error',
          'Failed to update password. Please try again.'
        );
      }
    }
  }

  openImageModal() {
    const currentUrl = this.profileForm().imageUrl;
    const isExternal =
      currentUrl &&
      !currentUrl.includes(this.API) &&
      !currentUrl.includes('r2.dev');
    if (isExternal) {
      this.tempImageUrl.set(currentUrl);
    } else {
      this.tempImageUrl.set('');
    }
    this.showImageModal.set(true);
  }

  closeImageModal() {
    this.showImageModal.set(false);
  }

  applyImageUrl() {
    if (this.tempImageUrl()) {
      this.profileForm.update((f) => ({ ...f, imageUrl: this.tempImageUrl() }));

      this.pendingFile = null;
      this.localPreviewUrl.set(null);

      this.closeImageModal();
    }
  }

  getProfileImage(): string {
    if (this.localPreviewUrl()) {
      return this.localPreviewUrl()!;
    }

    const url = this.profileForm().imageUrl;
    const isManaged = url.includes(this.API) || url.includes('r2.dev');
    if (isManaged) {
      return `${url}?t=${this.imageTimestamp()}`;
    }
    return url || '';
  }

  resetMessages() {
    this.successMessage.set('');
    this.errorMessage.set('');
  }

  resetProfileForm(user: any) {
    if (user) {
      this.profileForm.set({
        name: user.name || '',
        imageUrl: user.imageUrl || '',
      });
    }
  }

  openHelpModal() {
    this.showHelpModal.set(true);
  }

  closeHelpModal() {
    this.showHelpModal.set(false);
  }

  copyText(text: string) {
    navigator.clipboard.writeText(text);
    this.showMessage('success', 'Copied to clipboard!');
  }

  get inboundEmailAddress(): string {
    return environment.inboundEmail;
  }

  copyEmail() {
    navigator.clipboard.writeText(this.inboundEmailAddress);
    this.showMessage('success', 'Forwarding address copied to clipboard!');
  }
}
