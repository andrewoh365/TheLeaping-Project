import { forkJoin, MonoTypeOperatorFunction, timer } from 'rxjs';
import { map } from 'rxjs/operators';

/** Match the customer holdings loader without delaying the HTTP request itself. */
export function minimumLoadingTime<T>(): MonoTypeOperatorFunction<T> {
  return source => forkJoin({
    result: source,
    minimumDisplayTime: timer(150)
  }).pipe(map(({ result }) => result));
}