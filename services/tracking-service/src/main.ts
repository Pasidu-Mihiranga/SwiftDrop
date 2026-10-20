import "reflect-metadata";
import { NestFactory } from "@nestjs/core";
import { LOGGER, PinoNestLogger } from "@swiftdrop/common/nest";
import type { Logger } from "@swiftdrop/common";
import { AppModule } from "./app.module";
import { env } from "./config/env";

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });
  const logger = app.get<Logger>(LOGGER);
  app.useLogger(new PinoNestLogger(logger));
  app.enableShutdownHooks();
  await app.listen(env.PORT, "0.0.0.0");
  logger.info({ port: env.PORT }, "tracking-service started");
}

void bootstrap();
