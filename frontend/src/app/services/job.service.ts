import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Router } from '@angular/router';

export interface Job {
  id: string;
  company: string;
  role: string;
  location: string;
  appliedDate: string;
  updatedAt: string;
  status: string;
  stage: number;
  stageStatus: string;
  salaryMin: number;
  salaryMax: number;
  url?: string;
  notes?: string;
}

export interface DashboardStats {
  totalApplications: number;
  activePipeline: number;
  interviews: number;
  offers: number;
}

export interface ChartData {
  name: string;
  value: number;
}

export interface DashboardResponse {
  stats: DashboardStats;
  statusChart: ChartData[];
  monthlyChart: ChartData[];
  interviewChart: ChartData[];
}

export interface PagedResponse {
  content: Job[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

@Injectable({ providedIn: 'root' })
export class JobService {
  private readonly API = environment.apiBaseUrl;
  private http = inject(HttpClient);
  private router = inject(Router);
  private apiUrl = `${this.API}/api/jobs`;

  private jobsSignal = signal<Job[]>([]);
  readonly jobs = this.jobsSignal.asReadonly();

  readonly dashboardStats = signal<DashboardStats>({
    totalApplications: 0,
    activePipeline: 0,
    interviews: 0,
    offers: 0,
  });

  readonly statusDistribution = signal<ChartData[]>([]);
  readonly monthlyApplications = signal<ChartData[]>([]);

  readonly interviewProgress = signal<ChartData[]>([
    { name: 'Interviewed', value: 0 },
    { name: 'Not Interviewed', value: 0 },
  ]);

  showModal = signal(false);
  selectedJob = signal<Job | null>(null);

  private dashboardLoaded = false;
  private listLoaded = false;

  constructor() {}

  private refreshActiveView() {
    const currentUrl = this.router.url;

    this.dashboardLoaded = false;
    this.listLoaded = false;

    if (currentUrl.includes('/dashboard')) {
      this.loadDashboard(true);
    } else if (currentUrl.includes('/applications')) {
      this.loadJobs();
    } else {
      this.loadDashboard(true);
      this.loadJobs();
    }
  }

  async loadDashboard(force = false) {
    if (this.dashboardLoaded && !force) return;

    try {
      const data = await firstValueFrom(
        this.http.get<DashboardResponse>(`${this.apiUrl}/dashboard`),
      );

      this.dashboardStats.set(data.stats);
      this.statusDistribution.set(data.statusChart);
      this.monthlyApplications.set(data.monthlyChart);

      if (data.interviewChart && data.interviewChart.length >= 2) {
        this.interviewProgress.set(data.interviewChart);
      }

      this.dashboardLoaded = true;
    } catch (e) {
      console.error(e);
    }
  }

  private totalJobsSignal = signal(0);
  readonly totalJobs = this.totalJobsSignal.asReadonly();

  async loadJobs(
    page = 0,
    size = 8,
    sort = 'updatedAt',
    dir = 'desc',
    search = '',
    status = 'All Statuses',
  ) {
    try {
      const params: any = {
        page: page.toString(),
        size: size.toString(),
        sort: sort === 'date' ? 'updatedAt' : sort,
        dir,
        search,
        status,
      };

      const data = await firstValueFrom(
        this.http.get<PagedResponse>(this.apiUrl, { params }),
      );

      this.jobsSignal.set(data.content);
      this.totalJobsSignal.set(data.page.totalElements);
    } catch (e) {
      console.error(e);
    }
  }

  clearState() {
    this.jobsSignal.set([]);

    this.dashboardStats.set({
      totalApplications: 0,
      activePipeline: 0,
      interviews: 0,
      offers: 0,
    });

    this.statusDistribution.set([]);
    this.monthlyApplications.set([]);
    this.interviewProgress.set([
      { name: 'Interviewed', value: 0 },
      { name: 'Not Interviewed', value: 0 },
    ]);

    this.dashboardLoaded = false;
    this.listLoaded = false;
    this.showModal.set(false);
    this.selectedJob.set(null);
  }

  async addJob(job: any) {
    try {
      await firstValueFrom(this.http.post(this.apiUrl, job));
      this.refreshActiveView();
    } catch (err) {
      console.error(err);
    }
  }

  async updateJob(job: Job) {
    try {
      await firstValueFrom(this.http.put(`${this.apiUrl}/${job.id}`, job));
      this.refreshActiveView();
    } catch (err) {
      console.error(err);
    }
  }

  async deleteJob(id: string) {
    try {
      await firstValueFrom(this.http.delete(`${this.apiUrl}/${id}`));
      this.refreshActiveView();
    } catch (err) {
      console.error(err);
    }
  }

  openModal(job: Job | null = null) {
    this.selectedJob.set(job);
    this.showModal.set(true);
  }

  closeModal() {
    this.showModal.set(false);
    this.selectedJob.set(null);
  }
}
