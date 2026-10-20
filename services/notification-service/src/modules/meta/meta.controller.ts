import { Controller, Get } from "@nestjs/common";
import type { AuthenticatedUser } from "@swiftdrop/common";
import { CurrentUser } from "@swiftdrop/common/nest";

/** Smoke-test endpoint: proves the gateway → service → JWT chain works end to end. */
@Controller("api/v1/notifications/meta")
export class MetaController {
  @Get("whoami")
  whoAmI(@CurrentUser() user: AuthenticatedUser) {
    return { service: "notification-service", userId: user.id, roles: user.roles, merchantId: user.merchantId ?? null };
  }
}
