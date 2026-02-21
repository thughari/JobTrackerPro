import { Component } from '@angular/core';
import { LogoComponent } from '../ui/logo/logo.component';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-toc',
  standalone: true,
  imports: [CommonModule, RouterLink, LogoComponent],
  templateUrl: './toc.component.html',
  styleUrl: './toc.component.css'
})
export class TocComponent {
  currentYear = new Date().getFullYear();
}
