import { Routes } from '@angular/router';
import { LandingComponent } from './components/landing/landing.component';
import { LoginComponent } from './components/auth/login/login.component';
import { SignupComponent } from './components/auth/signup/signup.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { ApplicationListComponent } from './components/application-list/application-list.component';
import { LoginSuccessComponent } from './components/auth/login-success/login-success.component';
import { NotFoundComponent } from './components/not-found/not-found.component';
import { ProfileComponent } from './components/profile/profile.component';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { AboutComponent } from './components/about/about.component';
import { ResetPasswordComponent } from './components/auth/reset-password/reset-password.component';

export const routes: Routes = [
  { 
    path: '', 
    component: LandingComponent
  },
  { 
    path: 'about', 
    component: AboutComponent 
  },
  { 
    path: 'reset-password', 
    component: ResetPasswordComponent
  },
  { 
    path: 'login', 
    component: LoginComponent, 
    canActivate: [guestGuard] 
  },
  { 
    path: 'signup', 
    component: SignupComponent, 
    canActivate: [guestGuard] 
  },
  { 
    path: 'login-success', 
    component: LoginSuccessComponent, 
    canActivate: [guestGuard] 
  },
  
  {
    path: 'app',
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'applications', component: ApplicationListComponent },
      { path: 'profile', component: ProfileComponent },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', component: NotFoundComponent },
];
