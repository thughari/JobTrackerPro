import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-logo',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center gap-3 font-sans select-none group">
      
      <div [class]="sizeClasses" 
           class="relative flex items-center justify-center bg-gradient-to-br from-indigo-600 to-violet-700 text-white shadow-lg shadow-indigo-500/25 ring-1 ring-white/10 overflow-hidden transition-transform duration-300 group-hover:scale-105">
        
        <div class="absolute top-0 right-0 w-full h-full bg-gradient-to-bl from-white/10 to-transparent pointer-events-none"></div>

        <svg [class]="iconSize" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" class="relative z-10 drop-shadow-sm">
          
          <path d="M12 5 V16 C12 19 10 21 7.5 21 C5.5 21 4.5 19.5 4.5 17.5" 
                stroke="white" 
                stroke-width="3.5" 
                stroke-linecap="round" 
                stroke-linejoin="round"/>
          
          <circle cx="19" cy="6" r="2.5" fill="#4ade80" />
          
        </svg>
      </div>

      @if (showText) {
        <div class="flex flex-col justify-center">
          <span class="font-bold tracking-tight text-gray-900 dark:text-white leading-none" [class]="textClasses">
            JobTrack<span class="text-indigo-600 dark:text-indigo-400">Pro</span>
          </span>
        </div>
      }
    </div>
  `
})
export class LogoComponent {
  @Input() size: 'sm' | 'md' | 'lg' | 'xl' = 'md';
  @Input() showText = true;

  get sizeClasses(): string {
    switch (this.size) {
      case 'sm': return 'w-8 h-8 rounded-lg';
      case 'md': return 'w-10 h-10 rounded-xl';
      case 'lg': return 'w-12 h-12 rounded-[14px]';
      case 'xl': return 'w-16 h-16 rounded-2xl';
      default: return 'w-10 h-10 rounded-xl';
    }
  }

  get iconSize(): string {
    switch (this.size) {
      case 'sm': return 'w-4 h-4';
      case 'md': return 'w-5 h-5';
      case 'lg': return 'w-6 h-6';
      case 'xl': return 'w-8 h-8';
      default: return 'w-5 h-5';
    }
  }

  get textClasses(): string {
    switch (this.size) {
      case 'sm': return 'text-base';
      case 'md': return 'text-xl';
      case 'lg': return 'text-2xl';
      case 'xl': return 'text-4xl';
      default: return 'text-xl';
    }
  }
}