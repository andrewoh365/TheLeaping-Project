import { Injectable } from '@angular/core';

// Remembers that a user has just registered so the dashboard offers the
// walkthrough. Kept in localStorage so a page refresh doesn't lose it; it is
// cleared once the user finishes or skips the tour.
@Injectable({
  providedIn: 'root'
})
export class WalkthroughService {
  private readonly PENDING_KEY = 'leap.walkthroughPending';

  markPending(): void {
    try {
      localStorage.setItem(this.PENDING_KEY, 'true');
    } catch {
      // Storage can be blocked (e.g. private browsing); the tour is optional.
    }
  }

  isPending(): boolean {
    try {
      return localStorage.getItem(this.PENDING_KEY) === 'true';
    } catch {
      return false;
    }
  }

  clearPending(): void {
    try {
      localStorage.removeItem(this.PENDING_KEY);
    } catch {
      // Nothing to clear if storage is unavailable.
    }
  }
}
