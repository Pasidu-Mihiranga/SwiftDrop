import { Module } from "@nestjs/common";
import { DatabaseService } from "../../persistence/database.service";
import { TemplateService } from "../templates/template.service";
import { EmailChannel } from "../channels/email.channel";
import { MockSmsChannel } from "../channels/mock-sms.channel";
import { NotificationDispatcherService } from "../channels/notification-dispatcher.service";
import { NotificationConsumer } from "../../consumers/notification.consumer";
import { NotificationController } from "./notification.controller";

@Module({
  controllers: [NotificationController],
  providers: [
    DatabaseService,
    TemplateService,
    EmailChannel,
    MockSmsChannel,
    NotificationDispatcherService,
    NotificationConsumer,
  ],
  exports: [
    DatabaseService,
    TemplateService,
    NotificationDispatcherService,
  ],
})
export class NotificationModule {}
