import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay, tap } from 'rxjs/operators';
import { MessageBatch } from './batch.model';

@Injectable({ providedIn: 'root' })
export class BatchInjectionService {
  private batches: MessageBatch[] = [
    this.mock(new Date('2026-07-10T09:30:00')),
    this.mock(new Date('2026-07-12T14:05:00')),
    this.mock(new Date('2026-07-15T08:45:00'))
  ];
  private timers: { [id: string]: any } = {};

  getBatches(): Observable<MessageBatch[]> {
    return of(this.batches.map(b => ({ ...b }))).pipe(delay(200));
  }

  createBatch(start: Date, end: Date): Observable<MessageBatch> {
    const batch = this.mock(new Date());
    return of(batch).pipe(delay(600), tap(() => this.batches.push(batch)));
  }

  uploadBatch(file: File): Observable<MessageBatch> {
    const batch = this.mock(new Date());
    return of(batch).pipe(delay(600), tap(() => this.batches.push(batch)));
  }

  deleteBatch(id: string): Observable<void> {
    return of(void 0).pipe(delay(200), tap(() => {
      this.clearTimer(id);
      this.batches = this.batches.filter(b => b.id !== id);
    }));
  }

  startBatch(id: string): Observable<void> {
    return of(void 0).pipe(delay(200), tap(() => {
      const batch = this.batches.find(b => b.id === id);
      if (!batch) {
        return;
      }
      batch.status = 'RUNNING';
      batch.progress = 0;
      this.timers[id] = setInterval(() => {
        batch.progress = Math.min(100, batch.progress + Math.floor(Math.random() * 10) + 3);
        if (batch.progress >= 100) {
          batch.status = 'COMPLETED';
          this.clearTimer(id);
        }
      }, 1000);
    }));
  }

  stopBatch(id: string): Observable<void> {
    return of(void 0).pipe(delay(200), tap(() => {
      this.clearTimer(id);
      const batch = this.batches.find(b => b.id === id);
      if (batch) {
        batch.status = 'STOPPED';
      }
    }));
  }

  downloadBatch(id: string): Observable<Blob> {
    return of(new Blob([`mock batch ${id}`], { type: 'application/zip' })).pipe(delay(400));
  }

  private clearTimer(id: string): void {
    if (this.timers[id]) {
      clearInterval(this.timers[id]);
      delete this.timers[id];
    }
  }

  private mock(creationDate: Date): MessageBatch {
    const counts = [0, 0, 0, 0].map(() => Math.floor(Math.random() * 500) + 50);
    return {
      id: Math.random().toString(36).substring(2, 10),
      creationDate,
      totalMessages: counts.reduce((a, b) => a + b, 0),
      mtEmission: counts[0],
      mtReception: counts[1],
      mxEmission: counts[2],
      mxReception: counts[3],
      status: 'IDLE',
      progress: 0
    };
  }
}
