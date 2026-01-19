import { Injectable, signal, effect } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  isDarkMode = signal<boolean>(true);

  constructor() {
    const saved = localStorage.getItem('theme');
    const initial = saved ? saved === 'dark' : true;
    this.isDarkMode.set(initial);

    effect(() => {
      const isDark = this.isDarkMode();
      localStorage.setItem('theme', isDark ? 'dark' : 'light');
      if (isDark) {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
    });
  }

  toggle() {
    this.isDarkMode.update((v) => !v);
  }
}
