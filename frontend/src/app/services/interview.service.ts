import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface InterviewStartResponse {
  sessionId: string;
  status: string;
  message: string;
}

export interface InterviewQuestion {
  index: number;
  question: string;
}

export interface InterviewQuestionsResponse {
  questions: InterviewQuestion[];
  currentIndex: number;
  totalQuestions: number;
}

export interface InterviewAnswerResponse {
  score: number;
  feedback: string;
  improvementGap: string;
  nextQuestionIndex: number;
  completed: boolean;
}

export interface InterviewReportResponse {
  overallScore: number;
  answeredQuestions: number;
  totalQuestions: number;
  weakAreas: string[];
  improvementSuggestions: string[];
}

@Injectable({ providedIn: 'root' })
export class InterviewService {
  private http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiBaseUrl}/api/interviews`;

  private async withRetry<T>(fn: () => Promise<T>, retries = 1): Promise<T> {
    try {
      return await fn();
    } catch (err) {
      if (retries <= 0) throw err;
      return this.withRetry(fn, retries - 1);
    }
  }

  async startInterview(jobId: string) {
    return this.withRetry(() => firstValueFrom(this.http.post<InterviewStartResponse>(`${this.apiUrl}/start/${jobId}`, {})));
  }

  async getQuestions(sessionId: string) {
    return this.withRetry(() => firstValueFrom(this.http.get<InterviewQuestionsResponse>(`${this.apiUrl}/${sessionId}/questions`)));
  }

  async submitAnswer(sessionId: string, questionIndex: number, answer: string) {
    return this.withRetry(() =>
      firstValueFrom(
        this.http.post<InterviewAnswerResponse>(`${this.apiUrl}/${sessionId}/answers`, {
          questionIndex,
          answer,
        }),
      ),
    );
  }

  async getReport(sessionId: string) {
    return this.withRetry(() => firstValueFrom(this.http.get<InterviewReportResponse>(`${this.apiUrl}/${sessionId}/report`)));
  }

  async uploadResume(sessionId: string, file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.withRetry(() => firstValueFrom(this.http.post<{ status: string }>(`${this.apiUrl}/${sessionId}/resume`, formData)));
  }
}
