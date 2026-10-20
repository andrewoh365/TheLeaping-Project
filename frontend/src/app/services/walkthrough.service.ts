import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';

// Remembers which accounts have just registered so the dashboard offers them
// the walkthrough. The flag is stored per account (keyed by the signed-in
// email), so someone else using the same browser neither sees it nor clears
// it, and logging out keeps it for that account's next visit. Kept in
// localStorage so a refresh doesn't lose it; cleared once that account
// finishes or skips the tour.
@Injectable({
  providedIn: 'root'
})
export class WalkthroughService {
  private readonly PENDING_KEY_PREFIX = 'leap.walkthroughPending:';

  constructor(private authService: AuthService) {}

  markPending(): void {
    const key = this.pendingKey();
    if (!key) {
      return;
    }
    try {
      localStorage.setItem(key, 'true');
    } catch {
      // Storage can be blocked (e.g. private browsing); the tour is optional.
    }
  }

  isPending(): boolean {
    const key = this.pendingKey();
    if (!key) {
      return false;
    }
    try {
      return localStorage.getItem(key) === 'true';
    } catch {
      return false;
    }
  }

  clearPending(): void {
    const key = this.pendingKey();
    if (!key) {
      return;
    }
    try {
      localStorage.removeItem(key);
    } catch {
      // Nothing to clear if storage is unavailable.
    }
  }

  // null when nobody is signed in, so nothing is read or written.
  private pendingKey(): string | null {
    const email = this.authService.getSignedInEmail();
    return email ? `${this.PENDING_KEY_PREFIX}${email}` : null;
  }
}
