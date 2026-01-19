import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LogoComponent } from '../ui/logo/logo.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, LogoComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css'
})
export class LandingComponent {

}
