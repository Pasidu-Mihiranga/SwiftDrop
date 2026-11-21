import { Controller, Get, HttpCode, Param, Post, Query } from "@nestjs/common";
import { RequireRoles } from "@swiftdrop/common/nest";
import { DeliveryLogService } from "../application/delivery-log.service";
import { deliveryPageResponse, deliveryQuerySchema, deliveryResponse, parse } from "./dto";

/** Every merchant's deliveries, and replay of a dead-lettered one. Admins only. */
@Controller("api/v1/admin/webhooks")
@RequireRoles("ADMIN")
export class AdminWebhooksController {
  constructor(private readonly log: DeliveryLogService) {}

  @Get("deliveries")
  async deliveries(@Query() query: unknown) {
    const { status, page, size } = parse(deliveryQuerySchema, query);
    const { items, total } = await this.log.forAdmin(status, page, size);
    return deliveryPageResponse(items, total, page, size);
  }

  @Post("deliveries/:id/replay")
  @HttpCode(202)
  async replay(@Param("id") id: string) {
    return deliveryResponse(await this.log.replay(id));
  }
}
