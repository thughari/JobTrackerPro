import { Component, effect, inject, signal } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

declare var google: any;

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  private readonly API = environment.apiBaseUrl;
  isConnectingGmail = signal(false);

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
  isDisconnecting = signal(false);
  showDisconnectConfirm = signal(false);
  isDeleting = signal(false);
  imageTimestamp = signal(Date.now());
  showImageModal = signal(false);
  tempImageUrl = signal('');

  successMessage = signal('');
  errorMessage = signal('');

  pendingFile: File | null = null;
  localPreviewUrl = signal<string | null>(null);

  // Deletion related signals
  showDeleteConfirm = signal(false);
  isRequestingDeletion = signal(false);
  isCancelingDeletion = signal(false);
  deletionStatus = signal<any>(null);
  

  private messageTimeout: any;

  userProfile = this.authService.userProfile;

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



  connectGmail() {

    if (this.isConnectingGmail()) return;

    const userEmail = this.authService.userProfile()?.email;

    this.isConnectingGmail.set(true);

    try{
      const client = google.accounts.oauth2.initCodeClient({
        client_id: environment.googleClientId,
        scope: 'openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.labels https://www.googleapis.com/auth/gmail.settings.basic',
        ux_mode: 'popup',
        login_hint: userEmail,
        callback: (response: any) => {
          if (response.code) {
            this.authService.connectGmail(response.code).subscribe({
              next: () => {
                this.authService.fetchUserProfile(); 
                this.showMessage('success', 'Gmail Auto-Tracking Enabled!');
                this.isConnectingGmail.set(false);
              },
              error: (err) => {
                console.error('Backend link failed', err);
                this.showMessage('error', 'Failed to link Gmail account.');
                this.isConnectingGmail.set(false);
              }
            });
          }
        },
        error_callback: (error: any) => {
          console.warn('Google Auth Error:', error.type);
          this.isConnectingGmail.set(false);
          if (error.type === 'popup_blocked_by_browser') {
              this.showMessage('error', 'Please allow popups for this site.');
          }
        }
      });
      
      client.requestCode();
    } catch (err) {
      console.error('Google Auth Initialization Failed', err);
      this.showMessage('error', 'Failed to initiate Gmail connection.');
      this.isConnectingGmail.set(false);
    }
  }

  async disconnectGmail() {
    if (!confirm('Are you sure you want to disable Gmail automation?')) return;
    
    this.isDisconnecting.set(true);
    try {
      await firstValueFrom(this.authService.disconnectGmail());
      const current = this.authService.userProfile();
      if (current) {
        this.authService.userProfile.set({
          ...current,
          gmailConnected: false
        });
      }
      this.showMessage('success', 'Gmail disconnected successfully.');
    } catch (err) {
      this.showMessage('error', 'Failed to disconnect Gmail.');
      console.error('Disconnect failed', err);
    } finally {
      this.isDisconnecting.set(false);
    }
  }

  openDisconnectModal() {
    this.showDisconnectConfirm.set(true);
  }

  closeDisconnectModal() {
    if (this.isDeleting()) return;
    this.showDisconnectConfirm.set(false);
  }

  async confirmDisconnect() {
    this.isDeleting.set(true);
    try {
      await firstValueFrom(this.authService.disconnectGmail());
      
      const current = this.authService.userProfile();
      if (current) {
        this.authService.userProfile.set({
          ...current,
          gmailConnected: false
        });
      }
      
      this.showMessage('success', 'Gmail integration removed.');
      this.showDisconnectConfirm.set(false);
    } catch (err) {
      this.showMessage('error', 'Failed to disconnect Gmail.');
      console.error('Disconnect failed', err);
    } finally {
      this.isDeleting.set(false);
    }
  }

  openDeleteModal() {
    this.showDeleteConfirm.set(true);
  }

  closeDeleteModal() {
    this.showDeleteConfirm.set(false);
  }

  async requestDeletion() {
    this.isRequestingDeletion.set(true);
    try {
      await firstValueFrom(this.authService.requestDeletion());
      this.showMessage('success', 'Account deletion requested. You have 3 days to cancel.');
      this.showDeleteConfirm.set(false);
      this.authService.fetchUserProfile();
    } catch (err: any) {
      if (err.error && err.error.message) {
        this.showMessage('error', err.error.message);
      } else {
        this.showMessage('error', 'Failed to request account deletion.');
      }
    } finally {
      this.isRequestingDeletion.set(false);
    }
  }

  async cancelDeletion() {
    this.isCancelingDeletion.set(true);
    try {
      await firstValueFrom(this.authService.cancelDeletion());
      this.showMessage('success', 'Account deletion cancelled successfully.');
      this.authService.fetchUserProfile();
    } catch (err: any) {
      if (err.error && err.error.message) {
        this.showMessage('error', err.error.message);
      } else {
        this.showMessage('error', 'Failed to cancel deletion.');
      }
    } finally {
      this.isCancelingDeletion.set(false);
    }
  }

  getDeletionTimeRemaining(): string {
    const profile = this.userProfile();
    if (!profile?.daysUntilDeletion) return 'Account will be deleted soon';
    
    if (profile.daysUntilDeletion > 0) {
      return `${profile.daysUntilDeletion} day${profile.daysUntilDeletion > 1 ? 's' : ''} remaining`;
    } else {
      return 'Account will be deleted soon';
    }
  }
}
