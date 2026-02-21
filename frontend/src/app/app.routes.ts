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
import { PrivacyComponent } from './components/privacy/privacy.component';
import { ResourcesComponent } from './components/resources/resources.component';
import { TocComponent } from './components/toc/toc.component';

export const routes: Routes = [
  { 
    path: '', 
    component: LandingComponent,
    title: 'JobTrackerPro - Automate Your Job Hunt'
  },
  { 
    path: 'about', 
    component: AboutComponent,
    title: 'About JobTrackerPro - Architecture & Tech Stack'
  },
  { 
    path: 'privacy', 
    component: PrivacyComponent, 
    title: 'Privacy Policy'
  },
  { 
    path: 'terms', 
    component: TocComponent,
    title: 'Terms of Service - JobTrackerPro'
  },
  { 
    path: 'resources', 
    component: ResourcesComponent,
    title: 'Career Resources - JobTrackerPro'
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
      { path: 'resources', component: ResourcesComponent },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', component: NotFoundComponent },
];
