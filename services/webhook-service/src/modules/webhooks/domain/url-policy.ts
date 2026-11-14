import { lookup as dnsLookup } from "node:dns/promises";
import { BlockList, isIP } from "node:net";
import { ApiError } from "@swiftdrop/common";

export type HostLookup = (host: string) => Promise<string[]>;

export interface UrlPolicyOptions {
  /** Plain http is accepted only for local development and the demo network. */
  allowHttp: boolean;
  /** Private addresses are accepted only where the merchant simulator runs inside the same network. */
  allowPrivateNetworks: boolean;
  lookup?: HostLookup;
}

const BLOCKED = new BlockList();
for (const [network, prefix] of [["0.0.0.0", 8], ["10.0.0.0", 8], ["100.64.0.0", 10], ["127.0.0.0", 8],
  ["169.254.0.0", 16], ["172.16.0.0", 12], ["192.0.0.0", 24], ["192.168.0.0", 16], ["198.18.0.0", 15],
  ["224.0.0.0", 4], ["240.0.0.0", 4]] as const) {
  BLOCKED.addSubnet(network, prefix, "ipv4");
}
for (const [network, prefix] of [["::", 128], ["::1", 128], ["fc00::", 7], ["fe80::", 10], ["ff00::", 8]] as const) {
  BLOCKED.addSubnet(network, prefix, "ipv6");
}

/** Loopback, private, link-local (cloud metadata), carrier-grade NAT, multicast and reserved ranges. */
export function isBlockedAddress(address: string): boolean {
  const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/i.exec(address);
  if (mapped) return BLOCKED.check(mapped[1], "ipv4");
  const family = isIP(address);
  if (family === 0) return true;
  return BLOCKED.check(address, family === 4 ? "ipv4" : "ipv6");
}

const resolveAll: HostLookup = async (host) => (await dnsLookup(host, { all: true, verbatim: true })).map((a) => a.address);

/**
 * SSRF protection. Runs at registration and again before every POST, because a host name can be
 * pointed at an internal address after it was accepted.
 */
export class UrlPolicy {
  private readonly lookup: HostLookup;

  constructor(private readonly options: UrlPolicyOptions) {
    this.lookup = options.lookup ?? resolveAll;
  }

  async assertAllowed(raw: string): Promise<URL> {
    let url: URL;
    try {
      url = new URL(raw);
    } catch {
      throw rejected("The URL is not valid.");
    }
    if (url.protocol !== "https:" && !(url.protocol === "http:" && this.options.allowHttp)) {
      throw rejected("The URL must use https.");
    }
    if (url.username || url.password) throw rejected("The URL must not contain credentials.");
    if (this.options.allowPrivateNetworks) return url;
    const host = url.hostname.replace(/^\[(.*)]$/, "$1");
    let addresses: string[];
    try {
      addresses = isIP(host) ? [host] : await this.lookup(host);
    } catch {
      throw rejected("The host name could not be resolved.");
    }
    if (addresses.length === 0 || addresses.some(isBlockedAddress)) {
      throw rejected("The URL points to a private or reserved address.");
    }
    return url;
  }
}

function rejected(detail: string): ApiError {
  return new ApiError(400, "WEBHOOK_URL_REJECTED", detail);
}
