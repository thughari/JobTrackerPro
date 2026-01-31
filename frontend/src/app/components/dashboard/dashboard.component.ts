import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ThemeService } from '../../services/theme.service';
import { JobService } from '../../services/job.service';
import { CommonModule } from '@angular/common';
import { DonutChartComponent } from '../donut-chart/donut-chart.component';
import { BarChartComponent } from '../bar-chart/bar-chart.component';
import { GmailSetupModalComponent } from '../gmail-setup-modal/gmail-setup-modal.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    DonutChartComponent,
    BarChartComponent,
    GmailSetupModalComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  private jobService = inject(JobService);
  private themeService = inject(ThemeService);

  isRefreshing = signal(false);
  showHelpModal = signal(false);

  successMessage = signal('');
  errorMessage = signal('');
  private messageTimeout: any;

  stats = this.jobService.dashboardStats;
  statusData = this.jobService.statusDistribution;
  monthlyData = this.jobService.monthlyApplications;
  interviewData = this.jobService.interviewProgress;

  ngOnInit() {
    this.jobService.loadDashboard();
  }

  async onRefresh() {
    this.isRefreshing.set(true);
    await Promise.all([
      this.jobService.loadDashboard(true),
      this.jobService.loadJobs(),
    ]);
    this.isRefreshing.set(false);
  }

  handleModalMessage(event: { type: 'success' | 'error'; text: string }) {
    this.showMessage(event.type, event.text);
  }

  openHelpModal() {
    this.showHelpModal.set(true);
  }

  closeHelpModal() {
    this.showHelpModal.set(false);
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

  openAddModal() {
    this.jobService.openModal(null);
  }

  interviewRate = computed(() => {
    const total = this.stats().totalApplications;
    const interviews = this.stats().interviews;
    if (!total || total === 0) return 0;
    return Math.round((interviews / total) * 100);
  });

  offerRate = computed(() => {
    const interviews = this.stats().interviews;
    const offers = this.stats().offers;
    if (!interviews || interviews === 0) return 0;
    return Math.round((offers / interviews) * 100);
  });

  chartStrokeColor = computed(() =>
    this.themeService.isDarkMode() ? '#151A23' : '#ffffff',
  );

  gridColor = computed(() =>
    this.themeService.isDarkMode() ? '#374151' : '#e5e7eb',
  );

  textColor = computed(() =>
    this.themeService.isDarkMode() ? '#6b7280' : '#6b7280',
  );

  public statusColorMap: Record<string, string> = {
    Applied: '#6366f1',
    Shortlisted: '#a855f7',
    'Interview Scheduled': '#f59e0b',
    'Offer Received': '#10b981',
    Rejected: '#ef4444',
  };

  orderedStatusColors = computed(() => {
    return this.statusData().map(
      (item) => this.statusColorMap[item.name] || '#6b7280',
    );
  });

  interviewColors = ['#10b981', '#d1d5db'];
}
