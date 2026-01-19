import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ThemeService } from '../../services/theme.service';
import { JobService } from '../../services/job.service';
import { CommonModule } from '@angular/common';
import { DonutChartComponent } from '../donut-chart/donut-chart.component';
import { BarChartComponent } from '../bar-chart/bar-chart.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, DonutChartComponent, BarChartComponent],
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

  inboundEmail = environment.inboundEmail;
  
  readonly atsFilterQuery = `from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net)`;
  
  readonly subjectFilterQuery = `subject:("Application Received" OR "Application Confirmation" OR "Thank you for applying" OR "Interview Invitation" OR "Interview Request" OR "Coding Challenge" OR "Assessment Invitation" OR "Status of your application")`;

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
      this.jobService.loadJobs(true)
    ]);
    this.isRefreshing.set(false);
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

  copyEmail() {
    navigator.clipboard.writeText(this.inboundEmail);
    this.showMessage('success', 'Forwarding address copied to clipboard!');
  }

  copyText(text: string) {
    navigator.clipboard.writeText(text);
    this.showMessage('success', 'Copied to clipboard!');
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
    this.themeService.isDarkMode() ? '#151A23' : '#ffffff'
  );

  gridColor = computed(() =>
    this.themeService.isDarkMode() ? '#374151' : '#e5e7eb'
  );

  textColor = computed(() =>
    this.themeService.isDarkMode() ? '#6b7280' : '#6b7280'
  );

  public statusColorMap: Record<string, string> = {
    'Applied': '#6366f1',
    'Shortlisted': '#a855f7',
    'Interview Scheduled': '#f59e0b',
    'Offer Received': '#10b981',
    'Rejected': '#ef4444'
  };

  orderedStatusColors = computed(() => {
    return this.statusData().map(item => this.statusColorMap[item.name] || '#6b7280');
  });

  interviewColors = ['#10b981', '#d1d5db'];
}