import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import {
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Job, JobService } from '../../services/job.service';

type SortField = 'company' | 'role' | 'date' | 'status' | 'location';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, CurrencyPipe],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.css',
})
export class ApplicationListComponent implements OnInit, OnDestroy {

  private jobService = inject(JobService);

  searchQuery = signal('');
  statusFilter = signal('All Statuses');
  sortField = signal<SortField>('date');
  sortDirection = signal<SortDirection>('desc');
  currentPage = signal(1);
  pageSize = 8;

  intervalId : any;

  activeMenuId = signal<string | null>(null);

  jobs = this.jobService.jobs;

  ngOnInit() {
    this.jobService.loadJobs();

    this.intervalId = setInterval(() => {
      this.jobService.loadJobs(true); 
    }, 15000);
  }

  ngOnDestroy() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  filteredJobs = computed(() => {
    let result = this.jobs();
    const query = this.searchQuery().toLowerCase();
    const status = this.statusFilter();

    if (query) {
      result = result.filter(
        (job) =>
          job.company.toLowerCase().includes(query) ||
          job.role.toLowerCase().includes(query) ||
          job.location.toLowerCase().includes(query)
      );
    }

    if (status !== 'All Statuses') {
      result = result.filter((job) => job.status === status);
    }

    result = [...result].sort((a, b) => {
      const field = this.sortField();
      const direction = this.sortDirection() === 'asc' ? 1 : -1;

      let valA: string | number = a[field];
      let valB: string | number = b[field];

      if (field === 'date') {
        valA = new Date(a.date).getTime();
        valB = new Date(b.date).getTime();
      }

      if (typeof valA === 'string' && typeof valB === 'string') {
        valA = valA.toLowerCase();
        valB = valB.toLowerCase();
      }

      if (valA < valB) return -1 * direction;
      if (valA > valB) return 1 * direction;
      return 0;
    });

    return result;
  });

  paginatedJobs = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredJobs().slice(start, start + this.pageSize);
  });

  totalPages = computed(() =>
    Math.ceil(this.filteredJobs().length / this.pageSize)
  );

  toggleSort(field: SortField) {
    if (this.sortField() === field) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
  }

  getSortIcon(field: SortField) {
    if (this.sortField() !== field) return 'ph-arrows-down-up';
    return this.sortDirection() === 'asc' ? 'ph-arrow-up' : 'ph-arrow-down';
  }

  getStatusClass(status: string) {
    switch (status) {
      case 'Applied':
        return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
      case 'Shortlisted':
        return 'bg-purple-500/10 text-purple-400 border-purple-500/20';
      case 'Interview Scheduled':
        return 'bg-orange-500/10 text-orange-400 border-orange-500/20';
      case 'Offer Received':
        return 'bg-green-500/10 text-green-400 border-green-500/20';
      case 'Rejected':
        return 'bg-red-500/10 text-red-400 border-red-500/20';
      default:
        return 'bg-gray-500/10 text-gray-400';
    }
  }

  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.activeMenuId() === id) {
      this.activeMenuId.set(null);
    } else {
      this.activeMenuId.set(id);
    }
  }

  closeMenu() {
    this.activeMenuId.set(null);
  }

  onEditJob(job: Job) {
    this.jobService.openModal(job);
    this.activeMenuId.set(null);
  }

  deleteJob(id: string) {
    this.jobService.deleteJob(id);
    this.activeMenuId.set(null);
  }

  prevPage() {
    if (this.currentPage() > 1) this.currentPage.update((p) => p - 1);
  }

  nextPage() {
    if (this.currentPage() < this.totalPages())
      this.currentPage.update((p) => p + 1);
  }
}
