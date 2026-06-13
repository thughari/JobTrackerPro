import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Job, JobService } from '../../services/job.service';
import {
  debounceTime,
  distinctUntilChanged,
  firstValueFrom,
  Subject,
  Subscription,
} from 'rxjs';
import { AuthService } from '../../services/auth.service';

type SortField = 'company' | 'role' | 'date' | 'status' | 'location';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.css',
})
export class ApplicationListComponent implements OnInit, OnDestroy {
  private jobService = inject(JobService);
  public authService = inject(AuthService);

  searchQuery = signal('');
  statusFilter = signal('All Statuses');
  sortField = signal<SortField>('date');
  sortDirection = signal<SortDirection>('desc');
  currentPage = signal(0);
  pageSize = signal(8);
  isLocalSyncing = signal(false);
  isSyncing = computed(() => this.isLocalSyncing() || this.jobService.gmailSyncInProgress());
  syncStatus = this.jobService.gmailSyncStatus;

  successMessage = signal('');
  errorMessage = signal('');

  activeMenuId = signal<string | null>(null);

  jobs = this.jobService.jobs;
  totalElements = this.jobService.totalJobs;

  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  constructor() {
    effect(
      () => {
        this.loadData();
      },
      { allowSignalWrites: true },
    );
  }

  ngOnInit() {
    this.searchSubscription = this.searchSubject
      .pipe(debounceTime(400), distinctUntilChanged())
      .subscribe((val) => {
        this.currentPage.set(0);
        this.searchQuery.set(val);
      });

      this.jobService.startAutoRefresh();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
    this.jobService.stopAutoRefresh();
  }

  async onGmailSync() {
    if (this.isSyncing() || !this.authService.userProfile()?.gmailConnected) return;

    this.isLocalSyncing.set(true);
    try {
      await firstValueFrom(this.authService.syncGmail());
      this.showMessage('success', 'Gmail sync started in background.');
      await this.jobService.loadDashboard(true);
    } catch (err) {
      this.showMessage('error', 'Failed to start sync.');
    } finally {
      this.isLocalSyncing.set(false);
    }
  }

  showMessage(type: 'success' | 'error', text: string) {
    if (type === 'success') this.successMessage.set(text);
    else this.errorMessage.set(text);
    setTimeout(() => { this.successMessage.set('');
      this.errorMessage.set(''); 
    }, 5000);
  }

  onSearchInput(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchSubject.next(input.value);
  }

  onPageSizeChange(size: number) {
    this.pageSize.set(size);
    this.currentPage.set(0);
  }

  onStatusChange(status: string) {
    this.statusFilter.set(status);
    this.currentPage.set(0);
  }

  loadData() {
    this.jobService.loadJobs(
      this.currentPage(),
      this.pageSize(),
      this.sortField(),
      this.sortDirection(),
      this.searchQuery(),
      this.statusFilter(),
    );
  }

  toggleSort(field: SortField) {
    if (this.sortField() === field) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
    this.currentPage.set(0);
  }

  get totalPages() {
    return Math.ceil(this.totalElements() / this.pageSize());
  }

  get startIndex() {
    return this.currentPage() * this.pageSize() + 1;
  }

  get endIndex() {
    const end = (this.currentPage() + 1) * this.pageSize();
    return end > this.totalElements() ? this.totalElements() : end;
  }

  nextPage() {
    if (this.currentPage() + 1 < this.totalPages) {
      this.currentPage.update((p) => p + 1);
    }
  }

  prevPage() {
    if (this.currentPage() > 0) {
      this.currentPage.update((p) => p - 1);
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
    this.activeMenuId.update((current) => (current === id ? null : id));
  }

  closeMenu() {
    this.activeMenuId.set(null);
  }
  onEditJob(job: Job) {
    this.jobService.openModal(job);
    this.closeMenu();
  }
  deleteJob(id: string) {
    this.jobService.deleteJob(id);
    this.closeMenu();
  }
}
