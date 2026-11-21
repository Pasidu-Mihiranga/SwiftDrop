export interface NotificationPayload {
  toEmail?: string;
  toPhone?: string;
  recipientName?: string;
  subject: string;
  textBody: string;
  htmlBody: string;
  trackingNumber?: string;
  eventType: string;
  userId?: string;
}

export interface ChannelSendResult {
  success: boolean;
  channel: "EMAIL" | "SMS";
  recipient: string;
  error?: string;
}

export interface NotificationChannel {
  readonly name: "EMAIL" | "SMS";
  send(payload: NotificationPayload): Promise<ChannelSendResult>;
}
