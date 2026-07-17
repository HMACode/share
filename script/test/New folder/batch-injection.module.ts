import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { CalendarModule } from 'primeng/calendar';
import { FileUploadModule } from 'primeng/fileupload';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TabViewModule } from 'primeng/tabview';
import { TooltipModule } from 'primeng/tooltip';
import { BatchInjectionComponent } from './batch-injection.component';

@NgModule({
  declarations: [BatchInjectionComponent],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule.forChild([{ path: '', component: BatchInjectionComponent }]),
    TableModule,
    ButtonModule,
    DialogModule,
    CalendarModule,
    FileUploadModule,
    TagModule,
    ProgressBarModule,
    ToastModule,
    ConfirmDialogModule,
    TabViewModule,
    TooltipModule
  ]
})
export class BatchInjectionModule {}
