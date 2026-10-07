import { Component, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
  ReactiveFormsModule
} from '@angular/forms';
import {
  Router,
  ActivatedRoute,
  RouterLink
} from '@angular/router';
import { CommonModule } from '@angular/common';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  MatSnackBar,
  MatSnackBarModule
} from '@angular/material/snack-bar';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatSnackBarModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;

  loading = false;
  submitted = false;

  returnUrl = '';
  hidePassword = true;
  errorMessage = '';

  constructor(
    private formBuilder: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
    this.initializeForm();

    this.returnUrl =
      this.route.snapshot.queryParams['returnUrl'] || '';
  }

  private initializeForm(): void {
    this.loginForm = this.formBuilder.group({
      email: [
        '',
        [
          Validators.required,
          Validators.email
        ]
      ],

      password: [
        '',
        [
          Validators.required,
          Validators.minLength(6)
        ]
      ]
    });
  }

  get f() {
    return this.loginForm.controls;
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = '';

    if (this.loginForm.invalid) {
      return;
    }

    this.loading = true;

    const {
      email,
      password
    } = this.loginForm.value;

    this.authService
      .login(email, password)
      .subscribe({
        next: response => {
          /*
           * Preserve the token storage added on dev.
           *
           * AuthService also manages authentication state,
           * but this keeps compatibility with any existing
           * frontend code currently reading the "token" key.
           */
          localStorage.setItem(
            'token',
            response.token
          );

          this.snackBar.open(
            'Login successful!',
            'Close',
            {
              duration: 3000
            }
          );

          /*
           * Each role gets its own presentation landing page.
           * Admin reuses analytics with extra operations navigation.
           */
          const defaultRoute =
            response.role === 'ADMIN'
              ? '/admin'
              : response.role === 'ANALYST'
                ? '/analytics'
                : '/dashboard';

          /*
           * If an AuthGuard originally sent the user
           * to login with a returnUrl, respect it.
           * Otherwise use the role-based default route.
           */
          this.router.navigateByUrl(
            this.returnUrl || defaultRoute
          );
        },

        error: error => {
          this.loading = false;

          this.errorMessage =
            error.error?.message ||
            'Invalid email or password';

          this.snackBar.open(
            this.errorMessage,
            'Close',
            {
              duration: 5000
            }
          );
        }
      });
  }

  togglePasswordVisibility(): void {
    this.hidePassword =
      !this.hidePassword;
  }
}