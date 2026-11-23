import { Injectable, Logger } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import { DatabaseService, type NotificationDoc, type NotificationPreferenceDoc } from "../../persistence/database.service";
import { TemplateService, type TemplateData } from "../templates/template.service";
import { EmailChannel } from "./email.channel";
import { MockSmsChannel } from "./mock-sms.channel";
import type { NotificationPayload } from "./notification-channel.interface";

/** Events whose status is a parcel status, which the user can opt out of per status. */
const PARCEL_EVENTS = new Set(["ParcelCreated", "ParcelStatusChanged"]);

export interface DispatchNotificationRequest {
  eventId: string;
  eventType: string;
  userId?: string;
  toEmail?: string;
  toPhone?: string;
  recipientName?: string;
  trackingNumber?: string;
  status?: string;
  statusDescription?: string;
  origin?: string;
  destination?: string;
  fee?: string;
  courierName?: string;
  templateName: string;
}

@Injectable()
export class NotificationDispatcherService {
  private readonly logger = new Logger(NotificationDispatcherService.name);

  constructor(
    private readonly db: DatabaseService,
    private readonly templates: TemplateService,
    private readonly emailChannel: EmailChannel,
    private readonly mockSmsChannel: MockSmsChannel,
  ) {}

  async dispatch(req: DispatchNotificationRequest): Promise<void> {
    // 1. Check user preferences
    const prefs = await this.getUserPreferences(req.userId);

    // The per-status opt-outs are parcel statuses, so they only filter parcel events. Account notices
    // (merchant approved, rejected, suspended) carry a status too, but are always sent.
    const parcelEvent = PARCEL_EVENTS.has(req.eventType);
    const eventAllowed =
      !parcelEvent ||
      !req.status ||
      prefs.events.includes(req.status) ||
      (req.status === "CREATED" && prefs.events.includes("PARCEL_CREATED"));

    // 2. Render Template
    const templateData: TemplateData = {
      trackingNumber: req.trackingNumber,
      recipientName: req.recipientName,
      status: req.status,
      statusDescription: req.statusDescription,
      origin: req.origin,
      destination: req.destination,
      fee: req.fee,
      courierName: req.courierName,
    };

    const rendered = this.templates.render(req.templateName, templateData);

    const payload: NotificationPayload = {
      toEmail: req.toEmail,
      toPhone: req.toPhone,
      recipientName: req.recipientName,
      subject: rendered.subject,
      textBody: rendered.text,
      htmlBody: rendered.html,
      trackingNumber: req.trackingNumber,
      eventType: req.eventType,
      userId: req.userId,
    };

    // 3. Dispatch Email if enabled & recipient email exists
    if (prefs.email && eventAllowed && req.toEmail) {
      const result = await this.emailChannel.send(payload);
      await this.logDispatch(req.eventId, req.eventType, "EMAIL", result.recipient, result.success, result.error);
    }

    // 4. Dispatch SMS if enabled & recipient phone exists
    if (prefs.sms && eventAllowed && req.toPhone) {
      const result = await this.mockSmsChannel.send(payload);
      await this.logDispatch(req.eventId, req.eventType, "SMS", result.recipient, result.success, result.error);
    }

    // 5. Store In-App Notification if user ID is known
    if (req.userId) {
      await this.createInAppNotification({
        userId: req.userId,
        title: this.formatInAppTitle(rendered.subject, req.eventType),
        message: this.formatInAppMessage(rendered.text, req.destination),
        type: req.eventType,
        trackingNumber: req.trackingNumber,
      });
      await this.logDispatch(req.eventId, req.eventType, "IN_APP", req.userId, true);
    }
  }

  private formatInAppTitle(subject: string, eventType: string): string {
    let clean = subject.replace(/\[\s*SD[0-9A-Z]+\s*\]/gi, "").trim();
    clean = clean.replace(/^SwiftDrop(?:\s*Update)?:\s*/gi, "").trim();
    if (!clean) {
      if (eventType === "ParcelCreated") return "Booking Confirmed";
      if (eventType === "ParcelStatusChanged") return "Delivery Update";
      if (eventType === "ParcelAssigned") return "Courier Assigned";
      return "Notification";
    }
    return clean;
  }

  private formatInAppMessage(text: string, destination?: string): string {
    let clean = text.replace(/\s*Track(?:\s*live)?(?:\s*at)?[:\s]+https?:\/\/\S+/gi, "").trim();
    clean = clean.replace(/https?:\/\/\S+/gi, "").trim();
    clean = clean.replace(/^SwiftDrop(?:\s*Update)?:\s*/gi, "").trim();
    clean = clean.replace(
      /Your delivery booking\s+[A-Z0-9]+\s+has been confirmed\.\s*Destination:\s*([A-Za-z0-9\s-]+)\.?/gi,
      (_, city) => {
        const formattedCity = city.trim().charAt(0).toUpperCase() + city.trim().slice(1).toLowerCase();
        return `Delivery booking confirmed for ${formattedCity}.`;
      },
    );
    clean = clean.replace(/Parcel\s+[A-Z0-9]+\s+(?:is\s+now|has\s+been)\s+/gi, "");
    clean = clean.replace(/Note:\s*/gi, "");
    clean = clean.replace(/Destination:\s*([A-Z]{3,})\b/g, (_, city: string) => {
      return `Destination: ${city.charAt(0).toUpperCase()}${city.slice(1).toLowerCase()}`;
    });
    return clean.trim();
  }

  private async getUserPreferences(userId?: string): Promise<NotificationPreferenceDoc> {
    const defaultPrefs: NotificationPreferenceDoc = {
      _id: userId || "anonymous",
      email: true,
      sms: true,
      events: ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"],
      updatedAt: new Date(),
    };

    if (!userId) return defaultPrefs;

    try {
      const found = await this.db.preferences.findOne({ _id: userId });
      return found || defaultPrefs;
    } catch {
      return defaultPrefs;
    }
  }

  private async createInAppNotification(data: {
    userId: string;
    title: string;
    message: string;
    type: string;
    trackingNumber?: string;
  }): Promise<void> {
    try {
      const doc: NotificationDoc = {
        _id: randomUUID(),
        userId: data.userId,
        title: data.title,
        message: data.message,
        type: data.type,
        trackingNumber: data.trackingNumber,
        read: false,
        createdAt: new Date(),
      };
      await this.db.notifications.insertOne(doc);
    } catch (err) {
      this.logger.error(`Failed to store in-app notification for ${data.userId}: ${err}`);
    }
  }

  private async logDispatch(
    eventId: string,
    eventType: string,
    channel: "EMAIL" | "SMS" | "IN_APP",
    recipient: string,
    success: boolean,
    error?: string,
  ): Promise<void> {
    try {
      await this.db.notificationLogs.insertOne({
        _id: randomUUID(),
        eventId,
        eventType,
        channel,
        recipient,
        status: success ? "SUCCESS" : "FAILED",
        error,
        createdAt: new Date(),
      });
    } catch (err) {
      this.logger.warn(`Failed to write notification log: ${err}`);
    }
  }
}
