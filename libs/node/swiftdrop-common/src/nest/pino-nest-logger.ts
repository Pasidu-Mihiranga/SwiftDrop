import type { LoggerService } from "@nestjs/common";
import type { Logger } from "pino";

/** Routes Nest's own log output (startup, routing, errors) through the masked JSON pino logger. */
export class PinoNestLogger implements LoggerService {
  constructor(private readonly logger: Logger) {}

  log(message: unknown, context?: string) {
    this.logger.info({ context }, String(message));
  }

  error(message: unknown, trace?: string, context?: string) {
    this.logger.error({ context, trace }, String(message));
  }

  warn(message: unknown, context?: string) {
    this.logger.warn({ context }, String(message));
  }

  debug(message: unknown, context?: string) {
    this.logger.debug({ context }, String(message));
  }

  verbose(message: unknown, context?: string) {
    this.logger.trace({ context }, String(message));
  }
}
