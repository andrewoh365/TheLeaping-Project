import { Component, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';

import { InternalNavComponent } from '../internal-nav/internal-nav.component';

/**
 * Page frame for the admin and analyst screens: the shared top bar plus the
 * operations design system. Styles are unencapsulated so projected page content
 * can use the .ops-* classes, and every rule is scoped under .ops-app so nothing
 * reaches the customer dashboard.
 */
@Component({
  selector: 'app-internal-layout',
  standalone: true,
  imports: [CommonModule, InternalNavComponent],
  templateUrl: './internal-layout.component.html',
  styleUrls: ['./internal-layout.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class InternalLayoutComponent {}
