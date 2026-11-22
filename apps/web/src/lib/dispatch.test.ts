import { describe, expect, it, vi, afterEach } from "vitest";
import { businessDate, parcelAge, rankedCouriers, zoneMatches, dispatchRequest, type DispatchParcel, type Workload } from "./dispatch";
export const parcel: DispatchParcel = { parcelId:"p1",trackingNumber:"SD1",zone:"CP",sizeClass:"M",pickupCity:"Colombo",dropoffCity:"Kandy",createdAt:"2026-11-22T00:00:00Z",status:"CREATED",assignmentId:null,courierId:null,runDate:null };
const courier: Workload = {courierId:"c1",courierName:"One",homeZoneId:"z1",capacity:20,assignable:true,activeCount:3,deliveredToday:2,failedToday:1};
afterEach(() => vi.unstubAllGlobals());
describe("dispatch helpers", () => {
  it("uses Colombo dates across the UTC day boundary", () => {
    expect(businessDate(new Date("2026-11-21T20:00:00Z"))).toBe("2026-11-22");
  });
  it("shows parcel age and clamps clock skew", () => {
    const start = Date.parse(parcel.createdAt);
    expect(parcelAge(parcel.createdAt,start-100)).toBe("0m");
    expect(parcelAge(parcel.createdAt,start+120000)).toBe("2m");
    expect(parcelAge(parcel.createdAt,start+7200000)).toBe("2h");
    expect(parcelAge(parcel.createdAt,start+172800000)).toBe("2d");
  });
  it("maps zone UUIDs to codes and suggests lowest matching workload", () => {
    const zones = [{id:"z1",code:"CP",name:"Central"}];
    expect(zoneMatches(courier,parcel,zones)).toBe(true);
    const options = [courier,{...courier,courierId:"c2",homeZoneId:"z2",activeCount:0},{...courier,courierId:"c3",activeCount:1},{...courier,courierId:"c4",assignable:false}];
    expect(rankedCouriers(options,parcel,zones).map(c=>c.courierId)).toEqual(["c3","c1","c2"]);
    expect(rankedCouriers(options,{...parcel,courierId:"c3"},zones).map(c=>c.courierId)).not.toContain("c3");
  });
  it("uses the BFF and displays problem detail", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({detail:"Already assigned"}),{status:409})); vi.stubGlobal("fetch",fetch);
    await expect(dispatchRequest("dispatch/assignments")).rejects.toThrow("Already assigned");
    expect(fetch).toHaveBeenCalledWith("/api/bff/api/v1/dispatch/assignments",expect.objectContaining({cache:"no-store"}));
    fetch.mockResolvedValue(new Response("oops",{status:503}));
    await expect(dispatchRequest("dispatch/workloads")).rejects.toThrow("503");
    fetch.mockResolvedValue(new Response("[]")); expect(await dispatchRequest("dispatch/workloads")).toEqual([]);
  });
});
