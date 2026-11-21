import {
  Controller,
  Get,
  Post,
  Put,
  Param,
  Query,
  Body,
  HttpCode,
  HttpStatus,
  Logger,
} from "@nestjs/common";
import { CurrentUser, RequireRoles } from "@swiftdrop/common/nest";
import { type AuthenticatedUser, ApiError } from "@swiftdrop/common";
import { DatabaseService } from "../../persistence/database.service";

interface PreferencesDto {
  email: boolean;
  sms: boolean;
  events: string[];
}

@Controller("api/v1")
export class NotificationController {
  private readonly logger = new Logger(NotificationController.name);

  constructor(private readonly db: DatabaseService) {}

  @Get("notifications/me")
  async getMyNotifications(
    @CurrentUser() user: AuthenticatedUser,
    @Query("page") pageQuery = "0",
    @Query("size") sizeQuery = "20",
  ) {
    const page = Math.max(0, parseInt(pageQuery, 10) || 0);
    const size = Math.min(100, Math.max(1, parseInt(sizeQuery, 10) || 20));
    const skip = page * size;

    const [items, totalElements, unreadCount] = await Promise.all([
      this.db.notifications
        .find({ userId: user.id })
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(size)
        .toArray(),
      this.db.notifications.countDocuments({ userId: user.id }),
      this.db.notifications.countDocuments({ userId: user.id, read: false }),
    ]);

    const totalPages = Math.ceil(totalElements / size);

    return {
      content: items,
      unreadCount,
      page: {
        page,
        size,
        totalElements,
        totalPages,
      },
    };
  }

  @Post("notifications/me/:notificationId/read")
  @HttpCode(HttpStatus.NO_CONTENT)
  async markAsRead(
    @CurrentUser() user: AuthenticatedUser,
    @Param("notificationId") notificationId: string,
  ) {
    const res = await this.db.notifications.updateOne(
      { _id: notificationId, userId: user.id },
      { $set: { read: true } },
    );

    if (res.matchedCount === 0) {
      throw new ApiError(404, "NOT_FOUND", "Notification not found");
    }
  }

  @Get("notifications/preferences")
  async getPreferences(@CurrentUser() user: AuthenticatedUser) {
    const found = await this.db.preferences.findOne({ _id: user.id });
    if (found) {
      return {
        email: found.email,
        sms: found.sms,
        events: found.events,
      };
    }

    return {
      email: true,
      sms: true,
      events: ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"],
    };
  }

  @Put("notifications/preferences")
  async updatePreferences(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: PreferencesDto,
  ) {
    const email = body.email !== false;
    const sms = body.sms !== false;
    const rawEvents = Array.isArray(body.events)
      ? body.events
      : ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"];
    const events = rawEvents.map((e) => (e === "PARCEL_CREATED" ? "CREATED" : e));

    await this.db.preferences.updateOne(
      { _id: user.id },
      {
        $set: {
          email,
          sms,
          events,
          updatedAt: new Date(),
        },
      },
      { upsert: true },
    );

    return { email, sms, events };
  }

  @Get("admin/notifications/sms-inbox")
  @RequireRoles("ADMIN", "DISPATCHER")
  async getSmsInbox(
    @Query("limit") limitQuery = "50",
  ) {
    const limit = Math.min(100, Math.max(1, parseInt(limitQuery, 10) || 50));

    const messages = await this.db.smsOutbox
      .find({})
      .sort({ sentAt: -1 })
      .limit(limit)
      .toArray();

    return {
      messages,
      count: messages.length,
    };
  }
}
