import { Injectable, Logger } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import { DatabaseService } from "../../persistence/database.service";
import type { NotificationChannel, NotificationPayload, ChannelSendResult } from "./notification-channel.interface";

@Injectable()
export class MockSmsChannel implements NotificationChannel {
  readonly name = "SMS" as const;
  private readonly logger = new Logger(MockSmsChannel.name);

  constructor(private readonly db: DatabaseService) {}

  async send(payload: NotificationPayload): Promise<ChannelSendResult> {
    if (!payload.toPhone) {
      return { success: false, channel: "SMS", recipient: "N/A", error: "Missing recipient phone" };
    }

    const maskedPhone = this.maskPhone(payload.toPhone);

    try {
      await this.db.smsOutbox.insertOne({
        _id: randomUUID(),
        recipientPhoneMasked: maskedPhone,
        recipientPhoneRaw: payload.toPhone,
        body: payload.textBody,
        eventType: payload.eventType,
        trackingNumber: payload.trackingNumber,
        status: "SENT",
        sentAt: new Date(),
      });

      this.logger.log(`[Mock SMS] Dispatched to ${maskedPhone}: "${payload.textBody.slice(0, 80)}..."`);
      return { success: true, channel: "SMS", recipient: maskedPhone };
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : String(err);
      this.logger.error(`Failed to record mock SMS to ${maskedPhone}: ${errorMsg}`);
      return { success: false, channel: "SMS", recipient: maskedPhone, error: errorMsg };
    }
  }

  private maskPhone(phone: string): string {
    const cleaned = phone.replace(/\s+/g, "");
    if (cleaned.length < 7) return "***";
    const prefix = cleaned.slice(0, 3);
    const suffix = cleaned.slice(-3);
    return `${prefix}${"*".repeat(cleaned.length - 6)}${suffix}`;
  }
}
