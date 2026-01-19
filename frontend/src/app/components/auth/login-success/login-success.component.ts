import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-login-success',
  standalone: true,
  imports: [],
  templateUrl: './login-success.component.html',
  styleUrl: './login-success.component.css'
})

export class LoginSuccessComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.authService.handleToken(token);
    }
  }
}
