import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  RouterOutlet,
  Router,
  NavigationEnd,
  RouterLink,
  RouterLinkActive,
} from '@angular/router';
import { JobModalComponent } from './components/job-modal/job-modal.component';
import { Job, JobService } from './services/job.service';
import { ThemeService } from './services/theme.service';
import { AuthService } from './services/auth.service';
import { filter } from 'rxjs';
import { LogoComponent } from './components/ui/logo/logo.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    JobModalComponent,
    RouterOutlet,
    RouterLink,
    LogoComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  title = 'jobtrackerpro';
  private themeService = inject(ThemeService);
  public jobService = inject(JobService);
  public authService = inject(AuthService);
  private router = inject(Router);

  currentView = signal<'dashboard' | 'applications'>('dashboard');
  showModal = signal(false);
  selectedJob = signal<Job | null>(null);
  isDarkMode = this.themeService.isDarkMode;
  showLayout = false;

  isProfileMenuOpen = false;

  ngOnInit() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.showLayout = event.urlAfterRedirects.includes('/app');
      });
  }

  getInitials(): string {
    const name = this.authService.userProfile()?.name || 'User';
    return name
      .split(' ')
      .map((n) => n[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  toggleProfileMenu() {
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  closeMenu() {
    this.isProfileMenuOpen = false;
  }

  logout() {
    this.closeMenu();
    this.authService.logout();
  }

  setView(view: 'dashboard' | 'applications') {
    this.currentView.set(view);
  }

  toggleModal() {
    if (!this.showModal()) {
      this.selectedJob.set(null);
    }
    this.showModal.update((v) => !v);
  }

  openAddModal() {
    this.jobService.openModal(null);
  }

  openEditModal(job: Job) {
    this.selectedJob.set(job);
    this.showModal.set(true);
  }

  toggleTheme() {
    this.themeService.toggle();
  }

  exportCsv() {
    const jobs = this.jobService.jobs();
    if (!jobs.length) return;

    const headers = [
      'Company',
      'Role',
      'Location',
      'Date',
      'Status',
      'Stage',
      'Salary Min',
      'Salary Max',
      'URL',
      'Notes',
    ];

    const csvContent = [
      headers.join(','),
      ...jobs.map((job) =>
        [
          `"${job.company}"`,
          `"${job.role}"`,
          `"${job.location}"`,
          `"${job.date}"`,
          `"${job.status}"`,
          `"${job.stage}"`,
          `"${job.salaryMin || ''}"`,
          `"${job.salaryMax || ''}"`,
          `"${job.url || ''}"`,
          `"${job.notes ? job.notes.replace(/"/g, '""') : ''}"`,
        ].join(',')
      ),
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute(
      'download',
      `job-applications-${new Date().toISOString().split('T')[0]}.csv`
    );
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
}
