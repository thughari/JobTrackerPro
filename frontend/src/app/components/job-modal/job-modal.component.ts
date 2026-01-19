import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Job, JobService } from '../../services/job.service';

@Component({
  selector: 'app-job-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './job-modal.component.html',
  styleUrl: './job-modal.component.css',
})
export class JobModalComponent implements OnChanges, OnInit, OnDestroy {
  @Input() job: Job | null = null;
  @Output() close = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private jobService = inject(JobService);

  jobForm = this.fb.group({
    company: ['', Validators.required],
    role: ['', Validators.required],
    location: ['', Validators.required],
    date: [new Date().toISOString().split('T')[0], Validators.required],
    status: ['Applied', Validators.required],
    url: [''],
    salaryMin: [''],
    salaryMax: [''],
    notes: [''],
  });

  statusOptions = [
    'Applied',
    'Shortlisted',
    'Interview Scheduled',
    'Offer Received',
    'Rejected',
  ];

  ngOnInit() {
    document.body.style.overflow = 'hidden';
  }

  ngOnDestroy() {
    document.body.style.overflow = '';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['job'] && this.job) {
      this.jobForm.patchValue({
        company: this.job.company,
        role: this.job.role,
        location: this.job.location,
        date: this.job.date,
        status: this.job.status,
        url: this.job.url || '',
        salaryMin: this.job.salaryMin ? String(this.job.salaryMin) : '',
        salaryMax: this.job.salaryMax ? String(this.job.salaryMax) : '',
        notes: this.job.notes || '',
      });
    }
  }

  closeModal() {
    this.close.emit();
  }

  onSubmit() {
    if (this.jobForm.valid) {
      const formVal = this.jobForm.value;
      const status = formVal.status;

      let stage = 1;
      let stageStatus: Job['stageStatus'] = 'active';

      switch (status) {
        case 'Applied':
          stage = 1;
          stageStatus = 'active';
          break;
        case 'Shortlisted':
          stage = 2;
          stageStatus = 'active';
          break;
        case 'Interview Scheduled':
          stage = 3;
          stageStatus = 'active';
          break;
        case 'Offer Received':
          stage = 4;
          stageStatus = 'passed';
          break;
        case 'Rejected':
          stage = this.job ? this.job.stage : 1;
          stageStatus = 'failed';
          break;
        default:
          stage = 1;
          stageStatus = 'active';
      }

      const jobData = {
        company: formVal.company!,
        role: formVal.role!,
        location: formVal.location!,
        date: formVal.date!,
        status: formVal.status as any,
        stage: stage,
        stageStatus: stageStatus,
        salaryMin: Number(formVal.salaryMin) || 0,
        salaryMax: Number(formVal.salaryMax) || 0,
        url: formVal.url || '',
        notes: formVal.notes || '',
      };

      if (this.job) {
        const updatedJob: Job = { ...this.job, ...jobData };
        this.jobService.updateJob(updatedJob);
      } else {
        const newJob = { ...jobData };
        this.jobService.addJob(newJob);
      }
      this.closeModal();
    }
  }
}
