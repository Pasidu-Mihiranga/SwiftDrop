import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DispatchBoard } from "./dispatch-board";
import { RunSheet } from "./run-sheet";
import type { DispatchParcel } from "@/lib/dispatch";
const live = vi.hoisted(() => ({ events: [] as unknown[] }));
vi.mock("@/components/tracking/use-dispatch-feed", () => ({ useDispatchFeed: () => live.events }));
const parcel: DispatchParcel = {parcelId:"p1",trackingNumber:"SD1",zone:"CP",sizeClass:"M",pickupCity:"Colombo",dropoffCity:"Kandy",createdAt:new Date().toISOString(),status:"CREATED",assignmentId:null,courierId:null,runDate:null};
const couriers = [{courierId:"c1",courierName:"Courier One",homeZoneId:"z1",capacity:20,assignable:true,activeCount:2,deliveredToday:3,failedToday:1},{courierId:"c2",courierName:"Courier Two",homeZoneId:null,capacity:10,assignable:true,activeCount:0,deliveredToday:0,failedToday:0}];
let pool: DispatchParcel[], assigned: DispatchParcel[], failing: boolean;
const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
  let data: unknown;
  if (init?.method === "POST") {
    if (failing) return new Response(JSON.stringify({detail:"Parcel already assigned"}),{status:409});
    const body = JSON.parse(init.body as string);
    assigned = [{...parcel,assignmentId:"a1",courierId:body.courierId}]; pool = [];
    return new Response(JSON.stringify({id:"a1"}),{status:201});
  }
  if (url.includes("unassigned")) data = {content:pool,totalElements:pool.length,page:0,size:20};
  else if (url.includes("workloads")) data = couriers;
  else if (url.includes("zones")) data = [{id:"z1",code:"CP",name:"Central"}];
  else data = assigned;
  return new Response(JSON.stringify(data));
});
beforeEach(() => {
  pool=[parcel]; assigned=[]; failing=false; live.events=[]; fetchMock.mockClear(); vi.stubGlobal("fetch",fetchMock);
  HTMLDialogElement.prototype.showModal = function() { this.setAttribute("open",""); };
  HTMLDialogElement.prototype.close = function() { this.removeAttribute("open"); };
});
afterEach(() => vi.unstubAllGlobals());
describe("dispatch board", () => {
  it("assigns to suggested zone courier and permits reassignment", async () => {
    render(<DispatchBoard/>);
    expect(await screen.findByText("SD1")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button",{name:/^Assign$/}));
    expect(screen.getByText("Zone match")).toBeInTheDocument();
    expect(screen.getByRole("radio",{name:/Courier One/})).toBeChecked();
    await userEvent.click(screen.getByRole("button",{name:"Confirm assignment"}));
    expect(await screen.findByRole("status")).toHaveTextContent("Parcel assigned");
    expect(fetchMock).toHaveBeenCalledWith("/api/bff/api/v1/dispatch/assignments",expect.objectContaining({method:"POST",body:expect.stringContaining('"courierId":"c1"')}));
    await userEvent.click(await screen.findByRole("button",{name:"Reassign"}));
    expect(screen.queryByRole("radio",{name:/Courier One/})).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button",{name:"Confirm assignment"}));
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Parcel reassigned"));
    expect(fetchMock).toHaveBeenCalledWith("/api/bff/api/v1/dispatch/assignments/a1/reassign",expect.objectContaining({method:"POST",body:'{"courierId":"c2"}'}));
  });
  it("keeps allocation conflict visible and refreshes the pool", async () => {
    render(<DispatchBoard/>); await screen.findByText("SD1");
    await userEvent.click(screen.getByRole("button",{name:/^Assign$/})); failing=true;
    await userEvent.click(screen.getByRole("button",{name:"Confirm assignment"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("Parcel already assigned");
    await userEvent.click(screen.getByRole("button",{name:"Cancel"}));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("applies zone/date filters and refreshes on socket activity", async () => {
    const {rerender} = render(<DispatchBoard/>); await screen.findByText("SD1");
    fireEvent.change(screen.getByLabelText("Zone filter"),{target:{value:"CP"}});
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("zone=CP"),expect.anything()));
    fireEvent.change(screen.getByLabelText("Run date"),{target:{value:"2020-01-15"}});
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/bff/api/v1/dispatch/assignments?date=2020-01-15",expect.anything()));
    pool=[{...parcel,trackingNumber:"SD-NEW"}]; live.events=[{trackingNumber:"SD-NEW"}]; rerender(<DispatchBoard/>);
    expect(await screen.findByText("SD-NEW")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button",{name:"Refresh board"}));
  });
  it("reports API failures instead of presenting an empty pool as success", async () => {
    vi.stubGlobal("fetch",vi.fn().mockRejectedValue(new Error("Network unavailable")));
    render(<DispatchBoard/>); expect(await screen.findByRole("alert")).toHaveTextContent("Network unavailable");
  });
});
describe("courier run sheet", () => {
  it("loads only the server's run sheet and responds to date changes", async () => {
    assigned=[{...parcel,assignmentId:"a1",courierId:"c1"}]; render(<RunSheet/>);
    expect(await screen.findByRole("link",{name:"1. SD1"})).toHaveAttribute("href","/track/SD1");
    expect(screen.getByText("1 assigned parcels")).toBeInTheDocument();
    assigned=[]; fireEvent.change(screen.getByLabelText("Run date"),{target:{value:"2020-01-15"}});
    expect(await screen.findByText("No parcels assigned for this day.")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/bff/api/v1/courier/run-sheet?date=2020-01-15",expect.anything());
    await userEvent.click(screen.getByRole("button",{name:"Refresh run sheet"}));
  });
  it("shows run sheet errors", async () => {
    vi.stubGlobal("fetch",vi.fn().mockRejectedValue(new Error("Access denied")));
    render(<RunSheet/>); expect(await screen.findByRole("alert")).toHaveTextContent("Access denied");
  });
});
