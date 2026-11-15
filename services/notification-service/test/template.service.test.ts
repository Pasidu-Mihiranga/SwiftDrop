import { TemplateService } from "../src/modules/templates/template.service";

describe("TemplateService", () => {
  let service: TemplateService;

  beforeEach(() => {
    service = new TemplateService();
  });

  describe("parcel-created template", () => {
    it("renders valid subject, HTML and plain text with tracking link", () => {
      const rendered = service.render("parcel-created", {
        trackingNumber: "SD261115W5N9K4W",
        recipientName: "Saman Perera",
        destination: "Kandy",
        fee: "LKR 850.00",
      });

      expect(rendered.subject).toContain("SD261115W5N9K4W");
      expect(rendered.subject).toContain("Booking Confirmed");
      expect(rendered.html).toContain("SwiftDrop");
      expect(rendered.html).toContain("SD261115W5N9K4W");
      expect(rendered.html).toContain("Kandy");
      expect(rendered.html).toContain("Track Your Delivery Live");
      expect(rendered.text).toContain("SD261115W5N9K4W");
      expect(rendered.text).toContain("/track/SD261115W5N9K4W");
    });
  });

  describe("parcel-status-changed template", () => {
    it("renders dynamic status badge and note for DELIVERED", () => {
      const rendered = service.render("parcel-status-changed", {
        trackingNumber: "SD261115W5N9K4W",
        status: "DELIVERED",
        statusDescription: "Signed by recipient",
      });

      expect(rendered.subject).toContain("Delivered");
      expect(rendered.html).toContain("Delivered");
      expect(rendered.html).toContain("Signed by recipient");
      expect(rendered.text).toContain("is now Delivered");
    });

    it("renders OUT_FOR_DELIVERY status", () => {
      const rendered = service.render("parcel-status-changed", {
        trackingNumber: "SD261115W5N9K4W",
        status: "OUT_FOR_DELIVERY",
      });

      expect(rendered.subject).toContain("Out for Delivery");
      expect(rendered.text).toContain("Out for Delivery");
    });
  });

  describe("parcel-assigned template", () => {
    it("renders courier assignment notice", () => {
      const rendered = service.render("parcel-assigned", {
        trackingNumber: "SD261115W5N9K4W",
        courierName: "courier-007",
      });

      expect(rendered.subject).toContain("Courier Assigned");
      expect(rendered.html).toContain("courier-007");
      expect(rendered.text).toContain("courier-007");
    });
  });

  describe("merchant-lifecycle template", () => {
    it("renders approved notice for approved merchants", () => {
      const rendered = service.render("merchant-lifecycle", {
        merchantName: "Acme Retail Ltd",
        status: "APPROVED",
      });

      expect(rendered.subject).toContain("Approved");
      expect(rendered.html).toContain("approved");
      expect(rendered.html).toContain("/merchant");
    });
  });

  it("throws for unknown template", () => {
    expect(() => service.render("unknown-template", {})).toThrow("Unknown notification template");
  });
});
