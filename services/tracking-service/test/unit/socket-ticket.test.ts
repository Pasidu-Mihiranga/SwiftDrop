import { SocketTicketController } from "../../src/modules/realtime/socket-ticket.controller";
import { roomsForUser } from "../../src/modules/realtime/domain/rooms";
import { SocketTicketStore } from "../../src/modules/realtime/socket-ticket.store";
import type { AuthenticatedUser } from "@swiftdrop/common";
import { memoryRedis } from "../support/memory-redis";

const customer: AuthenticatedUser = { id: "11111111-0000-4000-8000-000000000001", roles: ["CUSTOMER"] };
const merchant: AuthenticatedUser = { id: "m-user", roles: ["MERCHANT"], merchantId: "00000000-0000-4000-8000-000000000001" };
const admin: AuthenticatedUser = { id: "a-1", roles: ["ADMIN"] };

describe("socket tickets", () => {
  it("issues a ticket that can be used exactly once", async () => {
    const redis = memoryRedis();
    const store = new SocketTicketStore(redis as never);
    const ticket = await store.issue(customer);
    expect(redis.set).toHaveBeenCalledWith(`socket-ticket:${ticket}`, expect.any(String), "EX", 60);
    expect(await store.consume(ticket)).toEqual({ id: customer.id, roles: ["CUSTOMER"], merchantId: undefined });
    expect(await store.consume(ticket)).toBeNull();
  });

  it("refuses an unknown ticket", async () => {
    const store = new SocketTicketStore(memoryRedis() as never);
    expect(await store.consume("not-issued")).toBeNull();
  });

  it("keeps only identifiers and roles in the ticket, never a name or e-mail", async () => {
    const redis = memoryRedis();
    const store = new SocketTicketStore(redis as never);
    await store.issue({ ...customer, email: "x@example.com", name: "Kamal Perera" });
    const stored = [...redis.values.values()][0];
    expect(stored).not.toContain("example.com");
    expect(stored).not.toContain("Kamal");
  });

  it("returns the ticket with the rooms that user may join", async () => {
    const store = new SocketTicketStore(memoryRedis() as never);
    const body = await new SocketTicketController(store).issue(merchant);
    expect(body.expiresIn).toBe(60);
    expect(body.rooms).toEqual([`user:m-user`, `merchant:00000000-0000-4000-8000-000000000001`]);
    expect(typeof body.ticket).toBe("string");
  });
});

describe("roomsForUser", () => {
  it("adds dispatch for staff and nothing extra for a customer", () => {
    expect(roomsForUser(customer)).toEqual([`user:${customer.id}`]);
    expect(roomsForUser(admin)).toEqual(["user:a-1", "dispatch"]);
  });
});
