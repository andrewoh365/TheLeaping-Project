import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostBinding,
  HostListener,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { WalkthroughStep } from './walkthrough-steps';

interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

// Space between the highlighted element and the yellow ring.
const HOLE_PADDING = 8;
// Space between the ring and the popup, and the popup and the screen edge.
const POPOVER_GAP = 16;
const SCREEN_MARGIN = 16;

// Timings for moving between steps. The fade durations must match the
// transitions in walkthrough.component.scss.
const FADE_OUT_MS = 400;
const PAUSE_BEFORE_HIGHLIGHT_MS = 350;
const SCROLL_MS_PER_PX = 0.9;
const SCROLL_MIN_MS = 600;
const SCROLL_MAX_MS = 1400;
// Gap left above a section that is too tall to centre on screen.
const SCROLL_TOP_MARGIN = 24;

// Ring shape, matching the SVG in the template.
const RING_STROKE = 3;
const RING_RADIUS = 12;
// The glowing pulse that travels around the ring.
const COMET_LENGTH = 110;
const COMET_PX_PER_MS = 0.3;
const COMET_MIN_LAP_MS = 2200;
const COMET_MAX_LAP_MS = 6000;

// Elements the Tab key can land on.
const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(', ');

/**
 * Dims the whole screen except the element the current step points at, draws
 * a yellow ring around it and shows a popup with the instructions.
 *
 * The dimming is four dark rectangles placed around the highlighted element,
 * so clicks on the rest of the page are blocked while the highlighted element
 * itself stays clickable. Positions are recalculated every animation frame,
 * which keeps the highlight in place while panels slide in, data loads or the
 * page scrolls.
 *
 * Moving to a new step fades the overlay out, scrolls to the next element,
 * pauses so the user can see the page, then fades the overlay back in.
 */
@Component({
  selector: 'app-walkthrough',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './walkthrough.component.html',
  styleUrls: ['./walkthrough.component.scss']
})
export class WalkthroughComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) step!: WalkthroughStep;
  @Input() stepIndex = 0;
  @Input() totalSteps = 0;
  @Input() marketOpen = false;

  @Output() start = new EventEmitter<void>();
  @Output() next = new EventEmitter<void>();
  @Output() skip = new EventEmitter<void>();

  @ViewChild('shadeTop', { static: true }) shadeTop!: ElementRef<HTMLElement>;
  @ViewChild('shadeBottom', { static: true }) shadeBottom!: ElementRef<HTMLElement>;
  @ViewChild('shadeLeft', { static: true }) shadeLeft!: ElementRef<HTMLElement>;
  @ViewChild('shadeRight', { static: true }) shadeRight!: ElementRef<HTMLElement>;
  @ViewChild('holeBlocker', { static: true }) holeBlocker!: ElementRef<HTMLElement>;
  @ViewChild('ring', { static: true }) ring!: ElementRef<SVGSVGElement>;
  @ViewChild('ringBase', { static: true }) ringBase!: ElementRef<SVGRectElement>;
  @ViewChild('cometTail', { static: true }) cometTail!: ElementRef<SVGRectElement>;
  @ViewChild('cometBody', { static: true }) cometBody!: ElementRef<SVGRectElement>;
  @ViewChild('cometHead', { static: true }) cometHead!: ElementRef<SVGRectElement>;
  @ViewChild('popover', { static: true }) popover!: ElementRef<HTMLElement>;

  // Fades the overlay in and out (see :host(.tour-visible) in the stylesheet).
  @HostBinding('class.tour-visible') visible = false;

  // What is on screen. Lags behind `step` while the overlay fades out, so the
  // text and highlight don't jump before they are hidden.
  displayedStep: WalkthroughStep | null = null;
  displayedIndex = 0;

  private animationFrame: number | null = null;
  private scrollFrame: number | null = null;
  private lastFrameTime: number | null = null;
  private cometProgress = 0;
  // Bumped on every step change so an older, slower transition stops early.
  private transitionId = 0;

  constructor(private zone: NgZone) {}

  get isWelcome(): boolean {
    return this.displayedIndex === 0;
  }

  get isLastStep(): boolean {
    return this.displayedIndex === this.totalSteps - 1;
  }

  get body(): string {
    const step = this.displayedStep;
    if (!step) {
      return '';
    }
    return this.marketOpen ? step.body : (step.bodyWhenMarketClosed ?? step.body);
  }

  ngAfterViewInit(): void {
    // Running outside Angular stops the per-frame loop from triggering change
    // detection on the whole dashboard 60 times a second.
    this.zone.runOutsideAngular(() => {
      const tick = (time: number) => {
        this.updatePositions(time);
        this.animationFrame = requestAnimationFrame(tick);
      };
      this.animationFrame = requestAnimationFrame(tick);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['step']) {
      this.showStep(this.step, this.stepIndex);
    }
  }

  ngOnDestroy(): void {
    this.transitionId++;
    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
    }
    this.cancelScroll();
  }

  onStart(): void {
    this.start.emit();
  }

  onNext(): void {
    if (this.isLastStep) {
      this.fadeOutThen(this.next);
    } else {
      this.next.emit();
    }
  }

  onSkip(): void {
    this.fadeOutThen(this.skip);
  }

  // The dark boxes only stop the mouse. Without this, Tab (or Shift+Tab) could
  // reach buttons hidden behind the overlay, e.g. Transfer while the trade
  // panel step is showing. Tab instead cycles through the highlighted area
  // (on steps waiting for the user) and the popup's buttons, and does nothing
  // while the overlay is changing between steps.
  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }
    event.preventDefault();

    const focusable = this.visible ? this.getFocusableElements() : [];
    if (focusable.length === 0) {
      return;
    }

    const current = focusable.indexOf(document.activeElement as HTMLElement);
    const direction = event.shiftKey ? -1 : 1;
    const nextIndex = current === -1
      ? (event.shiftKey ? focusable.length - 1 : 0)
      : (current + direction + focusable.length) % focusable.length;
    focusable[nextIndex].focus();
  }

  // Fade out, swap in the new step, scroll to it, pause, then fade back in.
  private async showStep(step: WalkthroughStep, index: number): Promise<void> {
    const id = ++this.transitionId;
    this.cancelScroll();
    // Move focus off whatever was just used (e.g. the Transfer button), which
    // is about to be covered by the overlay.
    this.focusPopover();

    if (this.visible) {
      this.visible = false;
      await this.wait(FADE_OUT_MS);
      if (id !== this.transitionId) {
        return;
      }
    }

    this.displayedStep = step;
    this.displayedIndex = index;

    await this.scrollToTarget(step);
    if (id !== this.transitionId) {
      return;
    }

    await this.wait(PAUSE_BEFORE_HIGHLIGHT_MS);
    if (id !== this.transitionId) {
      return;
    }

    this.cometProgress = 0;
    this.visible = true;
    this.focusPopover();
  }

  private focusPopover(): void {
    this.popover?.nativeElement.focus({ preventScroll: true });
  }

  // Highlighted area first (only on steps waiting for the user), then the popup.
  private getFocusableElements(): HTMLElement[] {
    const step = this.displayedStep;
    const target = step?.advanceOn ? this.findTarget(step) : null;
    const targetElements = target
      ? [target, ...Array.from(target.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))]
          .filter((element) => element.matches(FOCUSABLE_SELECTOR))
      : [];
    const popoverElements = Array.from(
      this.popover.nativeElement.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
    );
    // Skip anything not currently displayed (e.g. hidden by *ngIf or display: none).
    return [...targetElements, ...popoverElements].filter((element) => element.getClientRects().length > 0);
  }

  private async fadeOutThen(output: EventEmitter<void>): Promise<void> {
    const id = ++this.transitionId;
    this.cancelScroll();
    this.visible = false;
    await this.wait(FADE_OUT_MS);
    if (id === this.transitionId) {
      output.emit();
    }
  }

  private wait(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  private findTarget(step: WalkthroughStep | null): HTMLElement | null {
    if (!step?.target) {
      return null;
    }
    return document.querySelector<HTMLElement>(`[data-tour="${step.target}"]`);
  }

  // Scrolls the page with an ease-in-out curve, slower than the browser's
  // built-in smooth scroll, and resolves once the scroll has finished.
  private scrollToTarget(step: WalkthroughStep): Promise<void> {
    const target = this.findTarget(step);
    if (!target || this.isInFixedLayer(target)) {
      return Promise.resolve();
    }

    const rect = target.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
    // The dashboard header sticks to the top, so the usable area starts below it
    const topInset = target.closest('.top-nav-2') ? 0 : this.stickyHeaderBottom();
    if (rect.top >= topInset + SCREEN_MARGIN && rect.bottom <= viewportHeight - SCREEN_MARGIN) {
      return Promise.resolve();
    }

    const scroller = document.scrollingElement ?? document.documentElement;
    const startTop = scroller.scrollTop;
    const visibleHeight = viewportHeight - topInset;
    // Tall sections line up with the top so their heading stays on screen.
    const offset = rect.height > visibleHeight * 0.6
      ? rect.top - topInset - SCROLL_TOP_MARGIN
      : rect.top - topInset - (visibleHeight - rect.height) / 2;
    const endTop = this.clamp(startTop + offset, 0, scroller.scrollHeight - viewportHeight);
    const distance = endTop - startTop;

    if (Math.abs(distance) < 2) {
      return Promise.resolve();
    }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      scroller.scrollTop = endTop;
      return Promise.resolve();
    }

    const duration = this.clamp(Math.abs(distance) * SCROLL_MS_PER_PX, SCROLL_MIN_MS, SCROLL_MAX_MS);
    return new Promise((resolve) => {
      this.zone.runOutsideAngular(() => {
        const startTime = performance.now();
        const frame = (time: number) => {
          const progress = Math.min((time - startTime) / duration, 1);
          scroller.scrollTop = startTop + distance * this.easeInOutCubic(progress);
          if (progress < 1) {
            this.scrollFrame = requestAnimationFrame(frame);
          } else {
            this.scrollFrame = null;
            this.zone.run(() => resolve());
          }
        };
        this.scrollFrame = requestAnimationFrame(frame);
      });
    });
  }

  private cancelScroll(): void {
    if (this.scrollFrame !== null) {
      cancelAnimationFrame(this.scrollFrame);
      this.scrollFrame = null;
    }
  }

  // Bottom edge of a header pinned to the top of the window (0 when it scrolls away).
  private stickyHeaderBottom(): number {
    const header = document.querySelector<HTMLElement>('.top-nav-2');
    if (!header || getComputedStyle(header).position !== 'sticky') {
      return 0;
    }
    return Math.max(0, header.getBoundingClientRect().bottom);
  }

  // Elements inside a fixed panel (like the trade drawer) don't move when the page scrolls.
  private isInFixedLayer(element: HTMLElement): boolean {
    for (let node: HTMLElement | null = element; node; node = node.parentElement) {
      if (getComputedStyle(node).position === 'fixed') {
        return true;
      }
    }
    return false;
  }

  private updatePositions(time: number): void {
    const elapsed = this.lastFrameTime === null ? 0 : time - this.lastFrameTime;
    this.lastFrameTime = time;

    const viewportWidth = document.documentElement.clientWidth;
    const viewportHeight = window.innerHeight;
    const hole = this.getHole();

    if (!hole) {
      // No target: dim the whole screen and centre the popup.
      this.place(this.shadeTop, { top: 0, left: 0, width: viewportWidth, height: viewportHeight });
      this.hide(this.shadeBottom);
      this.hide(this.shadeLeft);
      this.hide(this.shadeRight);
      this.hide(this.holeBlocker);
      this.hide(this.ring);
      this.placePopover(null, viewportWidth, viewportHeight);
      return;
    }

    const holeBottom = hole.top + hole.height;
    const holeRight = hole.left + hole.width;
    this.place(this.shadeTop, { top: 0, left: 0, width: viewportWidth, height: hole.top });
    this.place(this.shadeBottom, { top: holeBottom, left: 0, width: viewportWidth, height: viewportHeight - holeBottom });
    this.place(this.shadeLeft, { top: hole.top, left: 0, width: hole.left, height: hole.height });
    this.place(this.shadeRight, { top: hole.top, left: holeRight, width: viewportWidth - holeRight, height: hole.height });
    this.place(this.ring, hole);
    this.drawRing(hole, elapsed);

    // Only let clicks through when the step is waiting for the user to do something there.
    if (this.displayedStep?.advanceOn) {
      this.hide(this.holeBlocker);
    } else {
      this.place(this.holeBlocker, hole);
    }

    this.placePopover(hole, viewportWidth, viewportHeight);
  }

  private getHole(): Box | null {
    const target = this.findTarget(this.displayedStep);
    if (!target) {
      return null;
    }
    const rect = target.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return null;
    }
    return {
      top: rect.top - HOLE_PADDING,
      left: rect.left - HOLE_PADDING,
      width: rect.width + HOLE_PADDING * 2,
      height: rect.height + HOLE_PADDING * 2
    };
  }

  // Sizes the solid ring to the hole and moves the glowing pulse along it.
  // The pulse is three stacked dashes (a wide faint tail, a brighter body and
  // a bright head) that share a front edge, so it fades out behind itself.
  private drawRing(hole: Box, elapsed: number): void {
    const width = Math.max(0, hole.width - RING_STROKE);
    const height = Math.max(0, hole.height - RING_STROKE);
    const radius = Math.min(RING_RADIUS, width / 2, height / 2);
    const perimeter = 2 * (width - 2 * radius) + 2 * (height - 2 * radius) + 2 * Math.PI * radius;

    // Laps take longer on bigger boxes, within limits, so the pulse feels
    // equally calm around a button and around a whole section.
    const lapMs = this.clamp(perimeter / COMET_PX_PER_MS, COMET_MIN_LAP_MS, COMET_MAX_LAP_MS);
    this.cometProgress = (this.cometProgress + elapsed / lapMs) % 1;
    const offset = -this.cometProgress * perimeter;
    const tailLength = Math.min(COMET_LENGTH, perimeter * 0.35);

    const rects: [ElementRef<SVGRectElement>, number][] = [
      [this.ringBase, 0],
      [this.cometTail, tailLength],
      [this.cometBody, tailLength * 0.55],
      [this.cometHead, tailLength * 0.2]
    ];
    for (const [rectRef, dashLength] of rects) {
      const rect = rectRef.nativeElement;
      rect.setAttribute('x', `${RING_STROKE / 2}`);
      rect.setAttribute('y', `${RING_STROKE / 2}`);
      rect.setAttribute('width', `${width}`);
      rect.setAttribute('height', `${height}`);
      rect.setAttribute('rx', `${radius}`);
      if (dashLength > 0) {
        // A zero-length dash, then a gap, then the visible dash: this pushes
        // shorter dashes forward so all three end at the same point.
        rect.setAttribute('stroke-dasharray', `0 ${tailLength - dashLength} ${dashLength} ${perimeter - tailLength}`);
        rect.setAttribute('stroke-dashoffset', `${offset}`);
      }
    }
  }

  // Tries below, above, right, then left of the highlight, using the first side
  // with enough room. If none fits (e.g. a section taller than the screen),
  // the popup sits at the bottom of the screen.
  private placePopover(hole: Box | null, viewportWidth: number, viewportHeight: number): void {
    const popover = this.popover.nativeElement;
    const width = popover.offsetWidth;
    const height = popover.offsetHeight;
    const clampLeft = (left: number) => this.clamp(left, SCREEN_MARGIN, viewportWidth - SCREEN_MARGIN - width);
    const clampTop = (top: number) => this.clamp(top, SCREEN_MARGIN, viewportHeight - SCREEN_MARGIN - height);

    let top: number;
    let left: number;

    if (!hole) {
      top = (viewportHeight - height) / 2;
      left = (viewportWidth - width) / 2;
    } else {
      const holeBottom = hole.top + hole.height;
      const holeRight = hole.left + hole.width;
      const centredLeft = clampLeft(hole.left + hole.width / 2 - width / 2);
      const centredTop = clampTop(hole.top + hole.height / 2 - height / 2);

      if (holeBottom + POPOVER_GAP + height <= viewportHeight - SCREEN_MARGIN) {
        top = holeBottom + POPOVER_GAP;
        left = centredLeft;
      } else if (hole.top - POPOVER_GAP - height >= SCREEN_MARGIN) {
        top = hole.top - POPOVER_GAP - height;
        left = centredLeft;
      } else if (holeRight + POPOVER_GAP + width <= viewportWidth - SCREEN_MARGIN) {
        top = centredTop;
        left = holeRight + POPOVER_GAP;
      } else if (hole.left - POPOVER_GAP - width >= SCREEN_MARGIN) {
        top = centredTop;
        left = hole.left - POPOVER_GAP - width;
      } else {
        top = viewportHeight - SCREEN_MARGIN - height;
        left = centredLeft;
      }
    }

    popover.style.top = `${Math.round(top)}px`;
    popover.style.left = `${Math.round(left)}px`;
  }

  private place(element: ElementRef<HTMLElement | SVGElement>, box: Box): void {
    const style = element.nativeElement.style;
    style.display = 'block';
    style.top = `${box.top}px`;
    style.left = `${box.left}px`;
    style.width = `${Math.max(0, box.width)}px`;
    style.height = `${Math.max(0, box.height)}px`;
  }

  private hide(element: ElementRef<HTMLElement | SVGElement>): void {
    element.nativeElement.style.display = 'none';
  }

  private easeInOutCubic(t: number): number {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), Math.max(min, max));
  }
}
