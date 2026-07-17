import { Component, OnDestroy, OnInit } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BatchStatus, MessageBatch } from './batch.model';
import { BatchInjectionService } from './batch-injection.service';

@Component({
  selector: 'app-batch-injection',
  templateUrl: './batch-injection.component.html',
  styleUrls: ['./batch-injection.component.css'],
  providers: [MessageService, ConfirmationService]
})
export class BatchInjectionComponent implements OnInit, OnDestroy {
  batches: MessageBatch[] = [];
  loading = true;
  createVisible = false;
  creating = false;
  startDate: Date | null = null;
  endDate: Date | null = null;
  private pollHandle: any;

  constructor(
    private batchService: BatchInjectionService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    clearTimeout(this.pollHandle);
  }

  load(): void {
    clearTimeout(this.pollHandle);
    this.batchService.getBatches().subscribe(batches => {
      this.batches = batches;
      this.loading = false;
      if (batches.some(b => b.status === 'RUNNING')) {
        this.pollHandle = setTimeout(() => this.load(), 1000);
      }
    });
  }

  start(batch: MessageBatch): void {
    this.batchService.startBatch(batch.id).subscribe(() => {
      this.messageService.add({ severity: 'info', summary: 'Injection started', detail: `Batch ${batch.id}` });
      this.load();
    });
  }

  stop(batch: MessageBatch): void {
    this.batchService.stopBatch(batch.id).subscribe(() => {
      this.messageService.add({ severity: 'warn', summary: 'Injection stopped', detail: `Batch ${batch.id}` });
      this.load();
    });
  }

  confirmDelete(batch: MessageBatch): void {
    this.confirmationService.confirm({
      message: `Delete batch ${batch.id}?`,
      header: 'Confirm deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.batchService.deleteBatch(batch.id).subscribe(() => {
        this.messageService.add({ severity: 'success', summary: 'Batch deleted', detail: `Batch ${batch.id}` });
        this.load();
      })
    });
  }

  download(batch: MessageBatch): void {
    this.batchService.downloadBatch(batch.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `batch-${batch.id}.zip`;
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  openCreate(): void {
    this.startDate = null;
    this.endDate = null;
    this.creating = false;
    this.createVisible = true;
  }

  createFromDates(): void {
    if (!this.startDate || !this.endDate) {
      return;
    }
    if (this.endDate <= this.startDate) {
      this.messageService.add({ severity: 'warn', summary: 'Invalid range', detail: 'End date must be after start date' });
      return;
    }
    this.creating = true;
    this.batchService.createBatch(this.startDate, this.endDate).subscribe(batch => this.onCreated(batch));
  }

  onUpload(event: { files: File[] }): void {
    this.creating = true;
    this.batchService.uploadBatch(event.files[0]).subscribe(batch => this.onCreated(batch));
  }

  statusSeverity(status: BatchStatus): string {
    switch (status) {
      case 'RUNNING': return 'info';
      case 'COMPLETED': return 'success';
      case 'STOPPED': return 'danger';
      default: return 'warning';
    }
  }

  private onCreated(batch: MessageBatch): void {
    this.creating = false;
    this.createVisible = false;
    this.messageService.add({ severity: 'success', summary: 'Batch created', detail: `Batch ${batch.id}` });
    this.load();
  }
}
