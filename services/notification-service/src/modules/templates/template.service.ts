import { Injectable, Logger } from "@nestjs/common";
import Handlebars from "handlebars";
import { env } from "../../config/env";

export interface TemplateData {
  trackingNumber?: string;
  recipientName?: string;
  senderName?: string;
  status?: string;
  statusDescription?: string;
  origin?: string;
  destination?: string;
  fee?: string;
  courierName?: string;
  courierPhone?: string;
  merchantName?: string;
  reason?: string;
  trackingUrl?: string;
  year?: number;
}

export interface RenderedTemplate {
  subject: string;
  html: string;
  text: string;
}

@Injectable()
export class TemplateService {
  private readonly logger = new Logger(TemplateService.name);

  constructor() {
    this.registerHelpers();
  }

  private registerHelpers(): void {
    Handlebars.registerHelper("eq", (a, b) => a === b);
    Handlebars.registerHelper("currentYear", () => new Date().getFullYear());
  }

  private formatCity(city?: string): string {
    if (!city) return "your address";
    const trimmed = city.trim();
    if (trimmed.toUpperCase() === trimmed && trimmed.length > 2) {
      return trimmed.charAt(0).toUpperCase() + trimmed.slice(1).toLowerCase();
    }
    return trimmed;
  }

  private userTrackingLink(trackingNumber?: string): string {
    return trackingNumber
      ? `https://swiftdrop.lk/track/${trackingNumber}`
      : `https://swiftdrop.lk/track`;
  }

  render(templateName: string, data: TemplateData): RenderedTemplate {
    const trackingUrl = data.trackingNumber
      ? `${env.FRONTEND_URL}/track/${data.trackingNumber}`
      : `${env.FRONTEND_URL}/track`;

    const context: TemplateData = {
      ...data,
      trackingUrl,
      year: new Date().getFullYear(),
    };

    switch (templateName) {
      case "parcel-created": {
        const dest = this.formatCity(data.destination);
        const trackLink = this.userTrackingLink(data.trackingNumber);
        return {
          subject: `SwiftDrop: Booking Confirmed [${data.trackingNumber}]`,
          html: this.renderParcelCreatedHtml(context),
          text: `SwiftDrop: Your delivery booking ${data.trackingNumber} has been confirmed for ${dest}. Track: ${trackLink}`,
        };
      }

      case "parcel-status-changed": {
        const readableStatus = this.humanStatus(data.status);
        const trackLink = this.userTrackingLink(data.trackingNumber);
        let message = `SwiftDrop: Your parcel ${data.trackingNumber} is now ${readableStatus}.`;

        if (data.status === "DELIVERED") {
          const detail = data.statusDescription ? ` (${data.statusDescription})` : "";
          message = `SwiftDrop: Your parcel ${data.trackingNumber} has been delivered${detail}. Thank you for using SwiftDrop!`;
        } else if (data.status === "PICKED_UP") {
          message = `SwiftDrop: Your parcel ${data.trackingNumber} has been picked up by our courier and is on its way.`;
        } else if (data.status === "IN_TRANSIT") {
          message = `SwiftDrop: Your parcel ${data.trackingNumber} is in transit to the delivery facility.`;
        } else if (data.status === "OUT_FOR_DELIVERY") {
          message = `SwiftDrop: Your parcel ${data.trackingNumber} is Out for Delivery today with your courier.`;
        } else if (data.status === "FAILED") {
          const detail = data.statusDescription ? `: ${data.statusDescription}` : "";
          message = `SwiftDrop: Delivery attempt for parcel ${data.trackingNumber} was unsuccessful${detail}. We will re-attempt shortly.`;
        } else if (data.status === "CANCELLED") {
          message = `SwiftDrop: Your delivery request for parcel ${data.trackingNumber} has been cancelled as requested.`;
        }

        return {
          subject: `SwiftDrop: Parcel ${readableStatus} [${data.trackingNumber}]`,
          html: this.renderStatusChangedHtml(context, readableStatus),
          text: `${message} Track: ${trackLink}`,
        };
      }

      case "parcel-assigned": {
        const trackLink = this.userTrackingLink(data.trackingNumber);
        return {
          subject: `SwiftDrop: Courier Assigned [${data.trackingNumber}]`,
          html: this.renderCourierAssignedHtml(context),
          text: `SwiftDrop: Courier ${data.courierName || "SwiftDrop Driver"} has been assigned to your delivery ${data.trackingNumber}. Track: ${trackLink}`,
        };
      }

      case "merchant-lifecycle": {
        const isApproved = data.status === "APPROVED";
        return {
          subject: `SwiftDrop: Merchant Account ${isApproved ? "Approved" : "Update"}`,
          html: this.renderMerchantLifecycleHtml(context, isApproved),
          text: `SwiftDrop: Your merchant application has been ${data.status ? data.status.toLowerCase() : "updated"}. Access your portal at https://swiftdrop.lk/merchant`,
        };
      }

      default:
        throw new Error(`Unknown notification template: ${templateName}`);
    }
  }

  private humanStatus(status?: string): string {
    switch (status) {
      case "PICKED_UP":
        return "Picked Up";
      case "IN_TRANSIT":
        return "In Transit";
      case "OUT_FOR_DELIVERY":
        return "Out for Delivery";
      case "DELIVERED":
        return "Delivered";
      case "FAILED":
        return "Delivery Attempt Failed";
      case "CANCELLED":
        return "Cancelled";
      default:
        return status || "Updated";
    }
  }

  private renderParcelCreatedHtml(c: TemplateData): string {
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Booking Confirmed</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }
    .header { background-color: #1e3a8a; padding: 24px; text-align: center; color: #ffffff; }
    .header h1 { margin: 0; font-size: 24px; font-weight: 700; }
    .content { padding: 32px 24px; }
    .pill { display: inline-block; background-color: #e0f2fe; color: #0369a1; padding: 6px 14px; border-radius: 9999px; font-weight: 600; font-size: 14px; margin-bottom: 16px; }
    .table { width: 100%; border-collapse: collapse; margin: 20px 0; }
    .table td { padding: 8px 0; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
    .table td.label { color: #64748b; width: 35%; }
    .table td.value { font-weight: 600; color: #0f172a; }
    .btn { display: block; text-align: center; background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 24px; }
    .footer { background-color: #f8fafc; padding: 16px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>SwiftDrop</h1>
      <p style="margin:4px 0 0;font-size:13px;opacity:0.8;">Express Courier & Parcel Delivery</p>
    </div>
    <div class="content">
      <div class="pill">Tracking #: ${c.trackingNumber}</div>
      <h2 style="margin:0 0 8px;font-size:18px;">Booking Confirmed!</h2>
      <p style="margin:0 0 16px;font-size:14px;color:#475569;">Hello ${c.recipientName || "Valued Customer"}, your delivery booking has been confirmed and scheduled for collection.</p>
      
      <table class="table">
        <tr><td class="label">Tracking Number</td><td class="value">${c.trackingNumber}</td></tr>
        <tr><td class="label">Destination</td><td class="value">${c.destination || "Address on file"}</td></tr>
        <tr><td class="label">Delivery Fee</td><td class="value">${c.fee || "Paid"}</td></tr>
        <tr><td class="label">Status</td><td class="value" style="color:#0284c7;">Booking Confirmed</td></tr>
      </table>

      <a href="${c.trackingUrl}" class="btn">Track Your Delivery Live</a>
    </div>
    <div class="footer">
      &copy; ${c.year} SwiftDrop Logistics. All rights reserved.
    </div>
  </div>
</body>
</html>`;
  }

  private renderStatusChangedHtml(c: TemplateData, readableStatus: string): string {
    const isDelivered = c.status === "DELIVERED";
    const statusColor = isDelivered ? "#059669" : "#2563eb";

    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Delivery Status Update</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }
    .header { background-color: #1e3a8a; padding: 24px; text-align: center; color: #ffffff; }
    .header h1 { margin: 0; font-size: 24px; font-weight: 700; }
    .content { padding: 32px 24px; }
    .badge { display: inline-block; background-color: #eff6ff; color: ${statusColor}; padding: 6px 14px; border-radius: 9999px; font-weight: 700; font-size: 14px; margin-bottom: 16px; }
    .btn { display: block; text-align: center; background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 24px; }
    .footer { background-color: #f8fafc; padding: 16px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>SwiftDrop</h1>
      <p style="margin:4px 0 0;font-size:13px;opacity:0.8;">Delivery Progress Notification</p>
    </div>
    <div class="content">
      <div class="badge">${readableStatus}</div>
      <h2 style="margin:0 0 8px;font-size:18px;">Your parcel is ${readableStatus}!</h2>
      <p style="margin:0 0 16px;font-size:14px;color:#475569;">
        ${isDelivered ? "Your parcel has been delivered successfully." : "There is a new update on your delivery progress."}
      </p>

      ${c.statusDescription ? `<div style="background:#f1f5f9;padding:12px;border-radius:8px;font-size:13px;color:#334155;margin-bottom:16px;"><strong>Courier Note:</strong> ${c.statusDescription}</div>` : ""}

      <p style="font-size:13px;color:#64748b;margin:0 0 8px;">Tracking Number: <strong>${c.trackingNumber}</strong></p>
      <a href="${c.trackingUrl}" class="btn">View Live Tracking Timeline</a>
    </div>
    <div class="footer">
      &copy; ${c.year} SwiftDrop Logistics. All rights reserved.
    </div>
  </div>
</body>
</html>`;
  }

  private renderCourierAssignedHtml(c: TemplateData): string {
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Courier Assigned</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }
    .header { background-color: #1e3a8a; padding: 24px; text-align: center; color: #ffffff; }
    .content { padding: 32px 24px; }
    .btn { display: block; text-align: center; background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 24px; }
    .footer { background-color: #f8fafc; padding: 16px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1 style="margin:0;font-size:24px;">SwiftDrop</h1>
    </div>
    <div class="content">
      <h2 style="margin:0 0 8px;font-size:18px;">Courier Assigned to Your Delivery</h2>
      <p style="margin:0 0 16px;font-size:14px;color:#475569;">
        Courier <strong>${c.courierName || "SwiftDrop Driver"}</strong> has been assigned to parcel <strong>${c.trackingNumber}</strong>.
      </p>
      <a href="${c.trackingUrl}" class="btn">Track Courier Live</a>
    </div>
    <div class="footer">&copy; ${c.year} SwiftDrop Logistics</div>
  </div>
</body>
</html>`;
  }

  private renderMerchantLifecycleHtml(c: TemplateData, isApproved: boolean): string {
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Merchant Account Notice</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }
    .header { background-color: #1e3a8a; padding: 24px; text-align: center; color: #ffffff; }
    .content { padding: 32px 24px; }
    .btn { display: block; text-align: center; background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 24px; }
    .footer { background-color: #f8fafc; padding: 16px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1 style="margin:0;font-size:24px;">SwiftDrop Merchant Services</h1>
    </div>
    <div class="content">
      <h2 style="margin:0 0 8px;font-size:18px;">
        ${isApproved ? "Welcome to SwiftDrop Partner Network!" : "Merchant Application Update"}
      </h2>
      <p style="margin:0 0 16px;font-size:14px;color:#475569;">
        ${isApproved
          ? "Congratulations! Your merchant business profile has been reviewed and approved by administrator. You can now generate API keys and dispatch bulk parcel deliveries."
          : `Your merchant status has been updated to: ${c.status}.`}
      </p>
      <a href="${env.FRONTEND_URL}/merchant" class="btn">Open Merchant Portal</a>
    </div>
    <div class="footer">&copy; ${c.year} SwiftDrop Logistics</div>
  </div>
</body>
</html>`;
  }
}
