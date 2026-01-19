import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

const params = new URLSearchParams(window.location.search);
const redirect = params.get('redirect');

if (redirect) {
  history.replaceState(null, '', decodeURIComponent(redirect));
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
