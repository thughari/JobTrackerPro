import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-gmail-setup-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './gmail-setup-modal.component.html',
  styleUrl: './gmail-setup-modal.component.css'
})
export class GmailSetupModalComponent {
  @Input() isVisible = false;
  @Output() onClose = new EventEmitter<void>();
  @Output() onMessage = new EventEmitter<{type: 'success' | 'error', text: string}>();

  activeStep = signal(1);
  isMobile = window.innerWidth < 768;
  inboundEmail = environment.inboundEmail;

  // Optimized Query List
  readonly atsFilterQuery = `from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net OR jobs2careers.com OR ziprecruiter.com OR monster.com OR careerbuilder.com OR wellfound.com OR lu.ma OR breezy.hr OR jazzhr.com OR comeet.com OR recruitee.com OR teamtailor.com OR applytojob.com OR jobs.github.com OR hackerrankforwork.com OR hackerrank.com OR hackerearth.com OR codility.com OR testgorilla.com OR hirevue.com OR vidcruiter.com OR codemetry.com OR pymetrics.com OR hired.com OR triplebyte.com)`;
  readonly subjectFilterQuery = `subject:("Application" OR "Applied" OR "Received" OR "Confirmation" OR "Interview" OR "Status" OR "Sollicitatie" OR "Engineer" OR "Developer" OR "Analyst" OR "Scientist" OR "Specialist" OR "Invitation" OR "Invite" OR "Assessment" OR "Challenge" OR "Test")`;
  readonly finalAtsQuery = `${this.atsFilterQuery} ${this.subjectFilterQuery}`;

  setStep(step: number) {
    if (step <= 4) this.activeStep.set(step);
  }

  copyText(text: string, label: string) {
    navigator.clipboard.writeText(text);
    this.onMessage.emit({type: 'success', text: `${label} copied!`});
  }

  openForwardingSettings() {
    this.copyText(this.inboundEmail, 'Address');
    window.open('https://mail.google.com/mail/u/0/#settings/fwdandpop', '_blank');
  }

  openGmailWithFilter() {
    this.copyText(this.finalAtsQuery, 'Filter Query');
    const encodedQuery = encodeURIComponent(this.finalAtsQuery);
    window.open(`https://mail.google.com/mail/u/0/#search/${encodedQuery}`, '_blank');
  }

  connectGmail() {
  // Redirect to your backend which then redirects to Google
  window.location.href = `${environment.apiBaseUrl}/api/auth/connect/gmail`;
}

  close() {
    this.onClose.emit();
    setTimeout(() => this.activeStep.set(1), 300);
  }
}