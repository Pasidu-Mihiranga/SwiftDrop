import { Injectable, Logger } from "@nestjs/common";
import nodemailer from "nodemailer";
import type SMTPTransport from "nodemailer/lib/smtp-transport";
import { env, type Env } from "../../config/env";
import type { NotificationChannel, NotificationPayload, ChannelSendResult } from "./notification-channel.interface";

/** Mailpit has no login and no TLS. A real relay uses the username and STARTTLS, or TLS on port 465. */
export function smtpTransportOptions(settings: Env): SMTPTransport.Options {
  const authenticated = settings.SMTP_USER.length > 0 && settings.SMTP_PASSWORD.length > 0;
  const secure = settings.SMTP_SECURE === "true" || (settings.SMTP_SECURE !== "false" && settings.SMTP_PORT === 465);
  return {
    host: settings.SMTP_HOST,
    port: settings.SMTP_PORT,
    secure,
    auth: authenticated ? { user: settings.SMTP_USER, pass: settings.SMTP_PASSWORD } : undefined,
    ignoreTLS: !authenticated && !secure,
    requireTLS: authenticated && !secure,
  };
}

@Injectable()
export class EmailChannel implements NotificationChannel {
  readonly name = "EMAIL" as const;
  private readonly logger = new Logger(EmailChannel.name);
  private readonly transporter: nodemailer.Transporter;

  constructor() {
    this.transporter = nodemailer.createTransport(smtpTransportOptions(env));
  }

  async send(payload: NotificationPayload): Promise<ChannelSendResult> {
    if (!payload.toEmail) {
      return { success: false, channel: "EMAIL", recipient: "N/A", error: "Missing recipient email" };
    }

    try {
      const info = await this.transporter.sendMail({
        from: env.SMTP_FROM,
        to: payload.toEmail,
        subject: payload.subject,
        text: payload.textBody,
        html: payload.htmlBody,
      });

      this.logger.log(`Email dispatched to ${this.maskEmail(payload.toEmail)} [MessageId: ${info.messageId}]`);
      return { success: true, channel: "EMAIL", recipient: this.maskEmail(payload.toEmail) };
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : String(err);
      this.logger.error(`Failed to send email to ${this.maskEmail(payload.toEmail)}: ${errorMsg}`);
      return { success: false, channel: "EMAIL", recipient: this.maskEmail(payload.toEmail), error: errorMsg };
    }
  }

  private maskEmail(email: string): string {
    const parts = email.split("@");
    if (parts.length !== 2) return "***@***.***";
    const user = parts[0];
    const maskedUser = user.length > 2 ? `${user.slice(0, 2)}***` : `${user}***`;
    return `${maskedUser}@${parts[1]}`;
  }
}
