
import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

interface Filter {
  startDate: Date;
  endDate: Date;
}

@Component({
  selector: 'app-message-replay',
  templateUrl: './message-replay.component.html',
  styleUrls: ['./message-replay.component.scss']
})
export class MessageReplayComponent implements OnInit {
  @Input() filter!: Filter;
  @Input() initialMessageCount: number = 0;
  @Output() recomputeRequested = new EventEmitter<{startDate: Date, endDate: Date}>();
  @Output() submitRequested = new EventEmitter<{startDate: Date, endDate: Date, destination: string}>();

  replayForm!: FormGroup;
  currentMessageCount: number = 0;
  hasDateChanged: boolean = false;
  showRecomputeButton: boolean = false;

  destinationOptions: string[] = [
    'Production Environment',
    'Staging Environment',
    'Development Environment',
    'Test Environment',
    'External System A',
    'External System B'
  ];

  constructor(private fb: FormBuilder) {}

  ngOnInit() {
    this.currentMessageCount = this.initialMessageCount;
    this.initializeForm();
    this.setupFormSubscriptions();
  }

  private initializeForm() {
    this.replayForm = this.fb.group({
      startDate: [this.filter.startDate, Validators.required],
      endDate: [this.filter.endDate, Validators.required],
      destination: ['', Validators.required]
    });
  }

  private setupFormSubscriptions() {
    this.replayForm.get('startDate')?.valueChanges.subscribe(() => {
      this.checkDateChanges();
    });

    this.replayForm.get('endDate')?.valueChanges.subscribe(() => {
      this.checkDateChanges();
    });
  }

  private checkDateChanges() {
    const currentStartDate = this.replayForm.get('startDate')?.value;
    const currentEndDate = this.replayForm.get('endDate')?.value;

    this.hasDateChanged =
      currentStartDate?.getTime() !== this.filter.startDate.getTime() ||
      currentEndDate?.getTime() !== this.filter.endDate.getTime();

    this.showRecomputeButton = this.hasDateChanged;
  }

  onRecompute() {
    const formData = this.replayForm.value;
    this.recomputeRequested.emit({
      startDate: formData.startDate,
      endDate: formData.endDate
    });
    this.hasDateChanged = false;
    this.showRecomputeButton = false;
  }

  onSubmit() {
    if (this.replayForm.valid && !this.hasDateChanged) {
      const formData = this.replayForm.value;
      this.submitRequested.emit({
        startDate: formData.startDate,
        endDate: formData.endDate,
        destination: formData.destination
      });
    }
  }

  get isSubmitDisabled(): boolean {
    return !this.replayForm.valid || this.hasDateChanged;
  }

  updateMessageCount(count: number) {
    this.currentMessageCount = count;
  }
}
