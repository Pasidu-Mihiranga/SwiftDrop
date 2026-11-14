import { Body, Controller, Delete, Get, HttpCode, Param, Post, Query } from "@nestjs/common";
import { ApiError, type AuthenticatedUser } from "@swiftdrop/common";
import { CurrentUser, RequireRoles } from "@swiftdrop/common/nest";
import { DeliveryLogService } from "../application/delivery-log.service";
import { EndpointService } from "../application/endpoint.service";
import { deliveryPageResponse, deliveryQuerySchema, endpointResponse, parse, registerEndpointSchema } from "./dto";

/** A merchant's own endpoints and delivery log. The merchant id always comes from the token. */
@Controller("api/v1/webhooks")
@RequireRoles("MERCHANT")
export class MerchantWebhooksController {
  constructor(private readonly endpoints: EndpointService, private readonly log: DeliveryLogService) {}

  @Post("endpoints")
  async register(@Body() body: unknown, @CurrentUser() user: AuthenticatedUser) {
    const input = parse(registerEndpointSchema, body);
    const { endpoint, secret } = await this.endpoints.register(merchantOf(user), input.url, input.eventTypes);
    return { ...endpointResponse(endpoint), secret };
  }

  @Get("endpoints")
  async list(@CurrentUser() user: AuthenticatedUser) {
    return (await this.endpoints.list(merchantOf(user))).map(endpointResponse);
  }

  @Delete("endpoints/:id")
  @HttpCode(204)
  async remove(@Param("id") id: string, @CurrentUser() user: AuthenticatedUser): Promise<void> {
    await this.endpoints.remove(merchantOf(user), id);
  }

  @Post("endpoints/:id/test")
  @HttpCode(202)
  async test(@Param("id") id: string, @CurrentUser() user: AuthenticatedUser) {
    return { deliveryId: await this.endpoints.sendTestPing(merchantOf(user), id), status: "QUEUED" };
  }

  @Get("deliveries")
  async deliveries(@Query() query: unknown, @CurrentUser() user: AuthenticatedUser) {
    const { status, page, size } = parse(deliveryQuerySchema, query);
    const { items, total } = await this.log.forMerchant(merchantOf(user), status, page, size);
    return deliveryPageResponse(items, total, page, size);
  }
}

/** A merchant user without a merchant_id claim has not finished onboarding and owns no endpoints. */
function merchantOf(user: AuthenticatedUser): string {
  if (!user.merchantId) throw new ApiError(403, "MERCHANT_REQUIRED", "This account is not linked to a merchant.");
  return user.merchantId;
}
