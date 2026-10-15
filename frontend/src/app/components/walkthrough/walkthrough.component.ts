import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
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

/**
 * Dims the whole screen except the element the current step points at, draws
 * a yellow ring around it and shows a popup with the instructions.
 *
 * The dimming is four dark rectangles placed around the highlighted element,
 * so clicks on the rest of the page are blocked while the highlighted element
 * itself stays clickable. Positions are recalculated every animation frame,
 * which keeps the highlight in place while panels slide in, data loads or the
 * page scrolls.
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
  @ViewChild('ring', { static: true }) ring!: ElementRef<HTMLElement>;
  @ViewChild('popover', { static: true }) popover!: ElementRef<HTMLElement>;

  private animationFrame: number | null = null;
  private scrollTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private zone: NgZone) {}

  get isWelcome(): boolean {
    return this.stepIndex === 0;
  }

  get isLastStep(): boolean {
    return this.stepIndex === this.totalSteps - 1;
  }

  get body(): string {
    return this.marketOpen ? this.step.body : (this.step.bodyWhenMarketClosed ?? this.step.body);
  }

  ngAfterViewInit(): void {
    // Running outside Angular stops the per-frame loop from triggering change
    // detection on the whole dashboard 60 times a second.
    this.zone.runOutsideAngular(() => {
      const tick = () => {
        this.updatePositions();
        this.animationFrame = requestAnimationFrame(tick);
      };
      this.animationFrame = requestAnimationFrame(tick);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['step']) {
      // Wait a moment so newly shown elements have rendered before scrolling.
      if (this.scrollTimer) {
        clearTimeout(this.scrollTimer);
      }
      this.scrollTimer = setTimeout(() => this.scrollTargetIntoView(), 50);
    }
  }

  ngOnDestroy(): void {
    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
    }
    if (this.scrollTimer) {
      clearTimeout(this.scrollTimer);
    }
  }

  private findTarget(): HTMLElement | null {
    if (!this.step.target) {
      return null;
    }
    return document.querySelector<HTMLElement>(`[data-tour="${this.step.target}"]`);
  }

  private scrollTargetIntoView(): void {
    const target = this.findTarget();
    if (!target) {
      return;
    }
    const rect = target.getBoundingClientRect();
    const fullyVisible = rect.top >= 0 && rect.bottom <= window.innerHeight;
    if (!fullyVisible) {
      // Tall sections line up with the top so their heading stays on screen.
      const block = rect.height > window.innerHeight * 0.6 ? 'start' : 'center';
      target.scrollIntoView({ behavior: 'smooth', block });
    }
  }

  private updatePositions(): void {
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

    // Only let clicks through when the step is waiting for the user to do something there.
    if (this.step.advanceOn) {
      this.hide(this.holeBlocker);
    } else {
      this.place(this.holeBlocker, hole);
    }

    this.placePopover(hole, viewportWidth, viewportHeight);
  }

  private getHole(): Box | null {
    const target = this.findTarget();
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

  private place(element: ElementRef<HTMLElement>, box: Box): void {
    const style = element.nativeElement.style;
    style.display = 'block';
    style.top = `${box.top}px`;
    style.left = `${box.left}px`;
    style.width = `${Math.max(0, box.width)}px`;
    style.height = `${Math.max(0, box.height)}px`;
  }

  private hide(element: ElementRef<HTMLElement>): void {
    element.nativeElement.style.display = 'none';
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), Math.max(min, max));
  }
}
