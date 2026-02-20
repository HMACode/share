import { Component, OnInit } from '@angular/core';

export interface MessageDetail {
  reference: string;
  type: string;
  flow: string;
  receptionDate: string;
  status: string;
  sender: string;
  receiver: string;
  application: string;
  trn: string;
}

@Component({
  selector: 'app-message-detail',
  templateUrl: './message-detail.component.html',
  styleUrls: ['./message-detail.component.scss']
})
export class MessageDetailComponent implements OnInit {

  message: MessageDetail = {
    reference: 'MSG-2024-0042891',
    type: 'MT103',
    flow: 'INBOUND',
    receptionDate: '2024-03-15 14:32:07',
    status: 'PROCESSED',
    sender: 'DEUTDEDB',
    receiver: 'BNPAFRPP',
    application: 'SWIFT-GW',
    trn: 'TRN20240315001234'
  };

  statusClass(): string {
    const map: Record<string, string> = {
      'PROCESSED': 'status-processed',
      'PENDING': 'status-pending',
      'REJECTED': 'status-rejected',
      'NACK': 'status-nack',
      'ACK': 'status-ack'
    };
    return map[this.message.status] || 'status-default';
  }

  ngOnInit(): void {}
}
