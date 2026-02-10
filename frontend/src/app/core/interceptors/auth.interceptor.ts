import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  const isRefreshRequest = req.url.endsWith('/api/auth/refresh');
  const isLogoutRequest = req.url.endsWith('/api/auth/logout');

  const authReq = token && !isRefreshRequest
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isRefreshRequest || isLogoutRequest || authReq.method === 'OPTIONS' || error.status !== 401) {
        return throwError(() => error);
      }

      return from(authService.refreshToken()).pipe(
        switchMap((refreshSucceeded) => {
          if (!refreshSucceeded) {
            authService.logout();
            return throwError(() => error);
          }

          const refreshedToken = authService.getAccessToken();
          if (!refreshedToken) {
            authService.logout();
            return throwError(() => error);
          }

          const retriedReq = req.clone({
            setHeaders: {
              Authorization: `Bearer ${refreshedToken}`
            }
          });
          return next(retriedReq);
        })
      );
    })
  );
};
