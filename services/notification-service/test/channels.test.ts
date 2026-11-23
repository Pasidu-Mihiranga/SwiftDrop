import { TemplateService } from "../src/modules/templates/template.service";
import { EmailChannel } from "../src/modules/channels/email.channel";
import { MockSmsChannel } from "../src/modules/channels/mock-sms.channel";
import { NotificationDispatcherService } from "../src/modules/channels/notification-dispatcher.service";

describe("Notification Channels & Dispatcher", () => {
  let mockDb: any;
  let templates: TemplateService;
  let emailChannel: EmailChannel;
  let mockSmsChannel: MockSmsChannel;
  let dispatcher: NotificationDispatcherService;

  beforeEach(() => {
    mockDb = {
      smsOutbox: {
        insertOne: jest.fn().mockResolvedValue({ acknowledged: true }),
      },
      preferences: {
        findOne: jest.fn(),
      },
      notifications: {
        insertOne: jest.fn().mockResolvedValue({ acknowledged: true }),
      },
      notificationLogs: {
        insertOne: jest.fn().mockResolvedValue({ acknowledged: true }),
      },
    };

    templates = new TemplateService();
    emailChannel = new EmailChannel();
    mockSmsChannel = new MockSmsChannel(mockDb);

    // Mock nodemailer sendMail to prevent real network calls
    (emailChannel as any).transporter = {
      sendMail: jest.fn().mockResolvedValue({ messageId: "msg-123" }),
    };

    dispatcher = new NotificationDispatcherService(mockDb, templates, emailChannel, mockSmsChannel);
  });

  describe("MockSmsChannel", () => {
    it("masks recipient phone number and stores message in outbox", async () => {
      const result = await mockSmsChannel.send({
        toPhone: "0771234567",
        subject: "Alert",
        textBody: "Your delivery is arriving soon",
        htmlBody: "<p>Arriving</p>",
        trackingNumber: "TRK-100",
        eventType: "ParcelStatusChanged",
      });

      expect(result.success).toBe(true);
      expect(result.channel).toBe("SMS");
      expect(result.recipient).toBe("077****567");
      expect(mockDb.smsOutbox.insertOne).toHaveBeenCalledWith(
        expect.objectContaining({
          recipientPhoneMasked: "077****567",
          recipientPhoneRaw: "0771234567",
          body: "Your delivery is arriving soon",
          status: "SENT",
        }),
      );
    });

    it("returns error if recipient phone is missing", async () => {
      const result = await mockSmsChannel.send({
        subject: "Alert",
        textBody: "Hi",
        htmlBody: "Hi",
        eventType: "Test",
      });

      expect(result.success).toBe(false);
      expect(result.error).toBe("Missing recipient phone");
    });
  });

  describe("EmailChannel", () => {
    it("sends email and masks email address in log", async () => {
      const result = await emailChannel.send({
        toEmail: "saman.perera@example.com",
        subject: "Booking Confirmed",
        textBody: "Your booking is confirmed",
        htmlBody: "<p>Your booking is confirmed</p>",
        eventType: "ParcelCreated",
      });

      expect(result.success).toBe(true);
      expect(result.channel).toBe("EMAIL");
      expect(result.recipient).toBe("sa***@example.com");
      expect((emailChannel as any).transporter.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: "saman.perera@example.com",
          subject: "Booking Confirmed",
        }),
      );
    });
  });

  describe("NotificationDispatcherService", () => {
    it("dispatches both email and SMS when preferences allow both", async () => {
      mockDb.preferences.findOne.mockResolvedValue({
        _id: "user-1",
        email: true,
        sms: true,
        events: ["CREATED", "DELIVERED"],
      });

      await dispatcher.dispatch({
        eventId: "evt-01",
        eventType: "ParcelCreated",
        userId: "user-1",
        toEmail: "user@example.com",
        toPhone: "0771234567",
        trackingNumber: "SD261115W5N9K4W",
        status: "CREATED",
        templateName: "parcel-created",
      });

      expect((emailChannel as any).transporter.sendMail).toHaveBeenCalled();
      expect(mockDb.smsOutbox.insertOne).toHaveBeenCalled();
      expect(mockDb.notifications.insertOne).toHaveBeenCalledWith(
        expect.objectContaining({
          userId: "user-1",
          trackingNumber: "SD261115W5N9K4W",
          read: false,
        }),
      );
    });

    it("skips SMS when user disabled SMS notifications", async () => {
      mockDb.preferences.findOne.mockResolvedValue({
        _id: "user-1",
        email: true,
        sms: false,
        events: ["CREATED"],
      });

      await dispatcher.dispatch({
        eventId: "evt-02",
        eventType: "ParcelCreated",
        userId: "user-1",
        toEmail: "user@example.com",
        toPhone: "0771234567",
        trackingNumber: "SD261115W5N9K4W",
        status: "CREATED",
        templateName: "parcel-created",
      });

      expect((emailChannel as any).transporter.sendMail).toHaveBeenCalled();
      expect(mockDb.smsOutbox.insertOne).not.toHaveBeenCalled();
    });

    it("skips channels when status is not in user preference events list", async () => {
      mockDb.preferences.findOne.mockResolvedValue({
        _id: "user-1",
        email: true,
        sms: true,
        events: ["DELIVERED"], // user only wants DELIVERED notifications
      });

      await dispatcher.dispatch({
        eventId: "evt-03",
        eventType: "ParcelStatusChanged",
        userId: "user-1",
        toEmail: "user@example.com",
        toPhone: "0771234567",
        trackingNumber: "SD261115W5N9K4W",
        status: "PICKED_UP", // not in allowed events
        templateName: "parcel-status-changed",
      });

      // Email and SMS should be suppressed because user opted out of PICKED_UP
      expect((emailChannel as any).transporter.sendMail).not.toHaveBeenCalled();
      expect(mockDb.smsOutbox.insertOne).not.toHaveBeenCalled();
      // In-app notification is still retained for audit & activity feed
      expect(mockDb.notifications.insertOne).toHaveBeenCalled();
    });

    it("always emails merchant account notices, whatever the parcel status preferences", async () => {
      mockDb.preferences.findOne.mockResolvedValue(null); // default preferences: parcel statuses only

      await dispatcher.dispatch({
        eventId: "evt-04",
        eventType: "MerchantLifecycleChanged",
        userId: "owner-1",
        toEmail: "orders@lankabooks.lk",
        recipientName: "Lanka Books (Pvt) Ltd",
        status: "APPROVED",
        templateName: "merchant-lifecycle",
      });

      expect((emailChannel as any).transporter.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({ to: "orders@lankabooks.lk", subject: "SwiftDrop: Merchant Account Approved" }),
      );
    });
  });
});
