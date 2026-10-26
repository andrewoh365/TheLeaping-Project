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

/** A one-click login for one of the BRS personas, mapped to a seeded demo account. */
interface DemoLogin {
  persona: string;
  role: string;
  email: string;
  password: string;
}

/*
 * Demo accounts for the three BRS personas (section 6). These are the seeded
 * development credentials (DataSeeder and V7__seed_internal_users.sql).
 *
 * Gated on ngDevMode rather than isDevMode(): the Angular CLI replaces ngDevMode
 * with `false` in production builds, so this list is stripped from the bundle
 * entirely instead of merely being hidden at runtime.
 */
declare const ngDevMode: boolean | undefined;

const DEMO_LOGINS: DemoLogin[] = [
  { persona: 'Joanna', role: 'Investor', email: 'john.trader@example.com', password: 'password123' },
  { persona: 'Priya', role: 'Analyst', email: 'analytics.analyst@leap.local', password: 'Analyst123!' },
  { persona: 'David', role: 'Operations', email: 'analytics.admin@leap.local', password: 'Admin123!' }
];

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

  readonly demoLogins: DemoLogin[] = typeof ngDevMode === 'undefined' || ngDevMode ? DEMO_LOGINS : [];
  activeDemo: string | null = null;

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
          this.activeDemo = null;

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

  /** Fill in a persona's demo credentials and sign in. */
  loginAs(demo: DemoLogin): void {
    if (this.loading) {
      return;
    }
    this.activeDemo = demo.persona;
    this.loginForm.setValue({ email: demo.email, password: demo.password });
    this.onSubmit();
  }

  togglePasswordVisibility(): void {
    this.hidePassword =
      !this.hidePassword;
  }
}