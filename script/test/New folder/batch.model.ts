export type BatchStatus = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'STOPPED';

export interface MessageBatch {
  id: string;
  creationDate: Date;
  totalMessages: number;
  mtEmission: number;
  mtReception: number;
  mxEmission: number;
  mxReception: number;
  status: BatchStatus;
  progress: number;
}
