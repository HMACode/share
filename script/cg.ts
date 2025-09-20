import { Component } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-profile-override',
  template: `
    <div class="container">
      <h2>Application Info</h2>
      <div class="info">
        <p><strong>Version:</strong> 1.0.0</p>
        <p><strong>User UID:</strong> 123456</p>
        <p><strong>Environment:</strong> Production</p>
      </div>
      <mat-divider></mat-divider>
      <div class="override-options">
        <div class="option">
          <mat-slide-toggle [(ngModel)]="uidEnabled" (change)="onToggle('uid')">Override UID</mat-slide-toggle>
          <mat-form-field *ngIf="uidEnabled" appearance="outline">
            <mat-label>New UID</mat-label>
            <input matInput formControlName="uid">
          </mat-form-field>
        </div>
        <mat-divider vertical></mat-divider>
        <div class="option">
          <mat-slide-toggle [(ngModel)]="entitlementsEnabled" (change)="onToggle('entitlements')">Override Entitlements</mat-slide-toggle>
          <mat-form-field *ngIf="entitlementsEnabled" appearance="outline">
            <mat-label>Entitlements (comma separated)</mat-label>
            <input matInput formControlName="entitlements">
          </mat-form-field>
        </div>
      </div>
      <div class="actions" *ngIf="uidEnabled || entitlementsEnabled">
        <button mat-flat-button color="primary" (click)="submit()">Submit</button>
      </div>
    </div>
  `,
  styles: [`
    .container { padding: 16px; font-family: Roboto, sans-serif; color: #333; }
    h2 { margin-bottom: 8px; font-weight: 500; }
    .info p { margin: 4px 0; }
    .override-options { display: flex; justify-content: space-between; align-items: flex-start; margin-top: 16px; }
    .option { flex: 1; display: flex; flex-direction: column; gap: 8px; }
    mat-divider[vertical] { margin: 0 16px; }
    .actions { margin-top: 16px; text-align: right; }
    @media(max-width: 600px) { .override-options { flex-direction: column; } mat-divider[vertical] { display: none; } }
  `]
})
export class ProfileOverrideComponent {
  form: FormGroup;
  uidEnabled = false;
  entitlementsEnabled = false;

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      uid: [''],
      entitlements: ['']
    });
  }

  onToggle(type: 'uid' | 'entitlements') {
    if (type === 'uid' && this.uidEnabled) this.entitlementsEnabled = false;
    if (type === 'entitlements' && this.entitlementsEnabled) this.uidEnabled = false;
  }

  submit() {
    console.log(this.form.value);
  }
}

