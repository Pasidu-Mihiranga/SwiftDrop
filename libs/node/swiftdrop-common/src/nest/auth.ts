import {
  type CanActivate,
  createParamDecorator,
  type ExecutionContext,
  Inject,
  Injectable,
  SetMetadata,
} from "@nestjs/common";
import { Reflector } from "@nestjs/core";
import { ApiError } from "../http/problem";
import { type AuthenticatedUser, bearerToken, type JwtVerifier, type Role } from "../auth/jwt";

export const JWT_VERIFIER = Symbol("SWIFTDROP_JWT_VERIFIER");
const IS_PUBLIC = "swiftdrop:isPublic";
const ROLES = "swiftdrop:roles";

/** Marks a route (or controller) as reachable without a token. */
export const Public = () => SetMetadata(IS_PUBLIC, true);

/** Requires at least one of the given realm roles. */
export const RequireRoles = (...roles: Role[]) => SetMetadata(ROLES, roles);

interface RequestWithUser {
  headers: Record<string, string | string[] | undefined>;
  user?: AuthenticatedUser;
}

/** Injects the caller: `whoAmI(@CurrentUser() user: AuthenticatedUser)`. */
export const CurrentUser = createParamDecorator((_data: unknown, ctx: ExecutionContext) => {
  return ctx.switchToHttp().getRequest<RequestWithUser>().user;
});

/**
 * Global guard: every route needs a valid Keycloak JWT unless marked @Public();
 * @RequireRoles() adds a role check. Mirrors the Java services' security defaults.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    @Inject(JWT_VERIFIER) private readonly verify: JwtVerifier,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const targets = [context.getHandler(), context.getClass()];
    if (this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, targets)) return true;

    const request = context.switchToHttp().getRequest<RequestWithUser>();
    const token = bearerToken(request.headers.authorization);
    if (!token) throw new ApiError(401, "UNAUTHORIZED", "A valid access token is required.");

    try {
      request.user = await this.verify(token);
    } catch {
      throw new ApiError(401, "UNAUTHORIZED", "A valid access token is required.");
    }

    const required = this.reflector.getAllAndOverride<Role[] | undefined>(ROLES, targets);
    if (required?.length && !required.some((role) => request.user?.roles.includes(role))) {
      throw new ApiError(403, "FORBIDDEN", "You do not have permission to perform this action.");
    }
    return true;
  }
}
