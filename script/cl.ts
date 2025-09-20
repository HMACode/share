import { Component } from '@angular/core';

@Component({
  selector: 'app-profile-override',
  template: `
    <div class="container">
      <mat-card class="info-card">
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">Version:</span>
              <span class="value">{{ appVersion }}</span>
            </div>
            <div class="info-item">
              <span class="label">User ID:</span>
              <span class="value">{{ currentUserId }}</span>
            </div>
            <div class="info-item">
              <span class="label">Environment:</span>
              <span class="value">{{ environment }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <div class="override-container">
        <div class="override-section">
          <div class="section-header">
            <mat-slide-toggle 
              [(ngModel)]="useCustomUid" 
              (change)="onUidToggle()"
              color="primary">
              Override with UID
            </mat-slide-toggle>
          </div>
          <mat-form-field appearance="outline" [disabled]="!useCustomUid">
            <mat-label>User ID</mat-label>
            <input matInput [(ngModel)]="customUid" [disabled]="!useCustomUid">
          </mat-form-field>
        </div>

        <mat-divider [vertical]="true" class="divider"></mat-divider>

        <div class="override-section">
          <div class="section-header">
            <mat-slide-toggle 
              [(ngModel)]="useCustomEntitlements" 
              (change)="onEntitlementsToggle()"
              color="primary">
              Override with Entitlements
            </mat-slide-toggle>
          </div>
          <mat-form-field appearance="outline" [disabled]="!useCustomEntitlements">
            <mat-label>Entitlements (comma separated)</mat-label>
            <textarea matInput [(ngModel)]="customEntitlements" rows="3" [disabled]="!useCustomEntitlements"></textarea>
          </mat-form-field>
        </div>
      </div>

      <div class="submit-container" *ngIf="useCustomUid || useCustomEntitlements">
        <button mat-raised-button color="primary" (click)="onSubmit()">
          Apply Override
        </button>
      </div>
    </div>
  `,
  styles: [`
    .container {
      padding: 16px;
      max-width: 600px;
      margin: 0 auto;
    }

    .info-card {
      margin-bottom: 24px;
      background: #fafafa;
    }

    .info-grid {
      display: grid;
      gap: 12px;
    }

    .info-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
    }

    .label {
      font-weight: 500;
      color: #666;
      min-width: 100px;
    }

    .value {
      font-family: 'Courier New', monospace;
      background: #f5f5f5;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 14px;
    }

    .override-container {
      display: flex;
      gap: 24px;
      margin-bottom: 24px;
    }

    .override-section {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .section-header {
      padding: 8px 0;
    }

    .divider {
      height: auto;
      margin: 0 12px;
    }

    .submit-container {
      text-align: center;
      padding-top: 16px;
      border-top: 1px solid #e0e0e0;
    }

    mat-form-field {
      width: 100%;
    }

    @media (max-width: 768px) {
      .override-container {
        flex-direction: column;
        gap: 16px;
      }
      
      .divider {
        display: none;
      }
      
      .container {
        padding: 12px;
      }
    }
  `]
})
export class ProfileOverrideComponent {
  appVersion = '2.1.4';
  currentUserId = 'usr_abc123def456';
  environment = 'production';
  
  useCustomUid = false;
  useCustomEntitlements = false;
  customUid = '';
  customEntitlements = '';

  onUidToggle() {
    if (this.useCustomUid) {
      this.useCustomEntitlements = false;
      this.customEntitlements = '';
    }
  }

  onEntitlementsToggle() {
    if (this.useCustomEntitlements) {
      this.useCustomUid = false;
      this.customUid = '';
    }
  }

  onSubmit() {
    const formData = {
      overrideType: this.useCustomUid ? 'uid' : 'entitlements',
      customUid: this.useCustomUid ? this.customUid : null,
      customEntitlements: this.useCustomEntitlements ? this.customEntitlements.split(',').map(e => e.trim()) : null,
      timestamp: new Date().toISOString()
    };
    
    console.log('Profile Override Form Data:', formData);
  }
}
