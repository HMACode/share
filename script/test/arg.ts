import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { PageEvent } from '@angular/material/paginator';

interface Result {
  total: number;
  results: ReplayRequest[];
}

interface ReplayRequest {
  requesterUid: string;
  starterUid: string;
  creationDate: string;
  startDate: string;
  flowType: string;
  destination: string;
}

@Component({
  selector: 'app-paginated-table',
  template: `
    <div class="table-container">
      <mat-table [dataSource]="dataSource" class="mat-elevation-4">
        
        <ng-container matColumnDef="requesterUid">
          <mat-header-cell *matHeaderCellDef>Requester UID</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.requesterUid}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="starterUid">
          <mat-header-cell *matHeaderCellDef>Starter UID</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.starterUid}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="creationDate">
          <mat-header-cell *matHeaderCellDef>Creation Date</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.creationDate}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="startDate">
          <mat-header-cell *matHeaderCellDef>Start Date</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.startDate}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="flowType">
          <mat-header-cell *matHeaderCellDef>Flow Type</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.flowType}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="destination">
          <mat-header-cell *matHeaderCellDef>Destination</mat-header-cell>
          <mat-cell *matCellDef="let element">{{element.destination}}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="actions">
          <mat-header-cell *matHeaderCellDef>Actions</mat-header-cell>
          <mat-cell *matCellDef="let element">
            <button mat-icon-button (click)="startAction(element)" matTooltip="Start replay request">
              <mat-icon>play_arrow</mat-icon>
            </button>
            <button mat-icon-button (click)="deleteAction(element)" matTooltip="Delete replay request">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-cell>
        </ng-container>

        <mat-header-row *matHeaderRowDef="displayedColumns"></mat-header-row>
        <mat-row *matRowDef="let row; columns: displayedColumns;"></mat-row>
      </mat-table>

      <mat-paginator 
        [length]="totalResults" 
        [pageSize]="pageSize" 
        [pageSizeOptions]="pageSizeOptions"
        (page)="onPageChange($event)"
        showFirstLastButtons>
      </mat-paginator>
    </div>
  `,
  styles: [`
    .table-container {
      width: 100%;
      overflow-x: auto;
    }
    
    mat-table {
      width: 100%;
      min-width: 800px;
    }
  `]
})
export class PaginatedTableComponent implements OnInit {
  displayedColumns: string[] = ['requesterUid', 'starterUid', 'creationDate', 'startDate', 'flowType', 'destination', 'actions'];
  dataSource: ReplayRequest[] = [];
  totalResults = 0;
  pageSize = 10;
  pageIndex = 0;
  pageSizeOptions: number[] = [5, 10, 25, 50];

  constructor(private service: any) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    const offset = this.pageIndex * this.pageSize;
    this.service.searchReplayRequests(offset, this.pageSize).subscribe((result: Result) => {
      this.dataSource = result.results;
      this.totalResults = result.total;
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  startAction(element: ReplayRequest): void {
    console.log('Start action for:', element);
  }

  deleteAction(element: ReplayRequest): void {
    console.log('Delete action for:', element);
  }
}
