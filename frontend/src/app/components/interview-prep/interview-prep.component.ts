import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { InterviewQuestion, InterviewReportResponse, InterviewService } from '../../services/interview.service';

@Component({
  selector: 'app-interview-prep',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './interview-prep.component.html',
})
export class InterviewPrepComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private interviewService = inject(InterviewService);

  sessionId = '';
  isLoading = signal(true);
  isSubmitting = signal(false);
  error = signal('');

  questions = signal<InterviewQuestion[]>([]);
  currentIndex = signal(0);
  answer = signal('');

  lastScore = signal<number | null>(null);
  lastFeedback = signal('');
  lastGap = signal('');

  report = signal<InterviewReportResponse | null>(null);
  uploadStatus = signal<'idle' | 'uploading' | 'processing' | 'done' | 'error'>('idle');

  async ngOnInit() {
    this.sessionId = this.route.snapshot.paramMap.get('sessionId') || '';
    if (!this.sessionId) {
      this.error.set('Interview session not found.');
      this.isLoading.set(false);
      return;
    }
    await this.loadQuestions();
  }

  async loadQuestions() {
    this.isLoading.set(true);
    this.error.set('');
    try {
      const res = await this.interviewService.getQuestions(this.sessionId);
      this.questions.set(res.questions || []);
      this.currentIndex.set(res.currentIndex || 0);
      if (!res.questions?.length) {
        await this.loadReport();
      }
    } catch (e) {
      this.error.set('Could not load interview questions. Please retry.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async submitAnswer() {
    if (this.isSubmitting() || !this.answer().trim()) return;

    this.isSubmitting.set(true);
    this.error.set('');
    try {
      const res = await this.interviewService.submitAnswer(this.sessionId, this.currentIndex(), this.answer());
      this.lastScore.set(res.score);
      this.lastFeedback.set(res.feedback);
      this.lastGap.set(res.improvementGap);
      this.answer.set('');
      this.currentIndex.set(res.nextQuestionIndex);
      if (res.completed) {
        await this.loadReport();
      }
    } catch (e) {
      this.error.set('Answer submission failed. Please retry.');
    } finally {
      this.isSubmitting.set(false);
    }
  }

  async loadReport() {
    try {
      const report = await this.interviewService.getReport(this.sessionId);
      this.report.set(report);
    } catch (e) {
      this.error.set('Unable to load final report.');
    }
  }

  async onResumeSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploadStatus.set('uploading');
    try {
      await this.interviewService.uploadResume(this.sessionId, file);
      this.uploadStatus.set('processing');
      setTimeout(() => this.uploadStatus.set('done'), 800);
    } catch {
      this.uploadStatus.set('error');
    }
  }
}
