import { Global, Module, type OnApplicationShutdown, type Provider } from "@nestjs/common";
import { type Connection, createConnection } from "mongoose";
import { env } from "../config/env";

export const MONGO_CONNECTION = Symbol("MONGO_CONNECTION");

const connectionProvider: Provider = {
  provide: MONGO_CONNECTION,
  useFactory: (): Connection => createConnection(env.MONGO_URI),
};

/** One MongoDB connection for the whole service (timelines and processed events). */
@Global()
@Module({ providers: [connectionProvider], exports: [MONGO_CONNECTION] })
export class MongoModule implements OnApplicationShutdown {
  constructor(private readonly connection: Connection) {}

  async onApplicationShutdown(): Promise<void> {
    await this.connection.close();
  }
}
