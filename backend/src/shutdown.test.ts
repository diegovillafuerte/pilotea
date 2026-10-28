/**
 * Graceful shutdown (TD-046 item 6): a real node:http server behind the Hono
 * adapter, wired the way src/index.ts wires it, with a gated in-flight route
 * and an injected pool and exit, so each phase of the drain is asserted in
 * order without sleeps.
 */

import http, { type Server } from "node:http";
import net, { type AddressInfo, type Socket } from "node:net";
import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { gracefulShutdown } from "./shutdown.js";

interface Reply {
  status: number | undefined;
  body: string;
  connection: string | undefined;
}

let server: Server;
let port: number;
let agent: http.Agent;
let entered: number; // handlers that reached the gate
let release: () => void;

beforeEach(() => {
  vi.spyOn(console, "log").mockImplementation(() => {}); // drain progress lines
  agent = new http.Agent({ keepAlive: true });
  entered = 0;
});

afterEach(() => {
  release();
  agent.destroy();
  server.closeAllConnections();
  if (server.listening) server.close();
  vi.restoreAllMocks();
});

/** Serve a test app through the drain, as src/index.ts does. */
async function start(pool: { end(): Promise<void> }, deadlineMs?: number) {
  const gate = new Promise<void>((resolve) => (release = resolve));
  const slow = async (c: { text(body: string): Response }) => {
    entered++;
    await gate;
    return c.text("done");
  };
  const app = new Hono();
  app.get("/slow", slow);
  app.get("/slow/:id", slow);
  app.get("/fast", (c) => c.text("fast")); // synchronous: the adapter writes it within the request event

  let resolveExit!: (code: number) => void;
  const exited = new Promise<number>((resolve) => (resolveExit = resolve));
  const exit = vi.fn((code: number) => resolveExit(code)); // records the code instead of ending the run
  const shutdown = gracefulShutdown(pool, { exit, deadlineMs });

  server = await new Promise<Server>((resolve) => {
    const s = serve({ fetch: shutdown.track(app.fetch), port: 0, hostname: "127.0.0.1" }, () =>
      resolve(s as Server),
    );
  });
  port = (server.address() as AddressInfo).port;
  return { onSignal: shutdown.attach(server), exit, exited };
}

/** GET over node:http, so keep-alive is explicit (the agent never times out idle sockets). */
function get(path: string): Promise<Reply> {
  return new Promise((resolve, reject) => {
    http
      .get({ host: "127.0.0.1", port, path, agent }, (res) => {
        let body = "";
        res.setEncoding("utf8");
        res.on("data", (chunk: string) => (body += chunk));
        res.on("end", () => resolve({ status: res.statusCode, body, connection: res.headers.connection }));
      })
      .on("error", reject);
  });
}

const whenEntered = (n: number) => vi.waitFor(() => expect(entered).toBe(n));
const nextTurn = () => new Promise((resolve) => setImmediate(resolve));
const fakePool = () => ({ end: vi.fn(async () => {}) });

/** A pool whose end() stays pending until `finish()`. */
function stalledPool() {
  let finish!: () => void;
  const ended = new Promise<void>((resolve) => (finish = resolve));
  return { end: vi.fn(() => ended), finish };
}

describe("gracefulShutdown", () => {
  it("lets an in-flight request finish, then closes the pool and exits 0", async () => {
    const pool = fakePool();
    const { onSignal, exit, exited } = await start(pool, 5_000);
    server.keepAliveTimeout = 60_000; // a held keep-alive socket would hit the deadline

    const inFlight = get("/slow");
    await whenEntered(1);
    onSignal("SIGTERM");

    expect(server.listening).toBe(false);
    await expect(get("/fast")).rejects.toMatchObject({ code: "ECONNREFUSED" });
    expect(pool.end).not.toHaveBeenCalled();
    expect(exit).not.toHaveBeenCalled();

    release();
    expect(await inFlight).toEqual({ status: 200, body: "done", connection: "close" });
    expect(await exited).toBe(0);
    expect(pool.end).toHaveBeenCalledTimes(1);
  });

  it("waits for a handler whose client hung up mid-request", async () => {
    const pool = fakePool();
    const { onSignal, exit, exited } = await start(pool, 5_000);

    const hungUp = http.get({ host: "127.0.0.1", port, path: "/slow", agent });
    hungUp.on("error", () => {}); // "socket hang up", from the destroy below
    await whenEntered(1);
    onSignal("SIGTERM");

    const serverClosed = new Promise((resolve) => server.once("close", resolve));
    hungUp.destroy();
    await serverClosed; // no connection is left, but the handler is still running
    await nextTurn();
    expect(pool.end).not.toHaveBeenCalled();
    expect(exit).not.toHaveBeenCalled();

    release();
    expect(await exited).toBe(0);
    expect(pool.end).toHaveBeenCalledTimes(1);
  });

  it("ignores a repeated signal", async () => {
    const pool = fakePool();
    const { onSignal, exit, exited } = await start(pool, 5_000);

    const inFlight = get("/slow");
    await whenEntered(1);
    onSignal("SIGTERM");
    onSignal("SIGTERM");
    onSignal("SIGINT");

    release();
    await inFlight;
    expect(await exited).toBe(0);
    await nextTurn();
    expect(pool.end).toHaveBeenCalledTimes(1);
    expect(exit).toHaveBeenCalledTimes(1);
  });

  it("waits for the pool to close before exiting 0", async () => {
    const pool = stalledPool();
    const { onSignal, exit, exited } = await start(pool, 5_000);

    onSignal("SIGTERM");
    await vi.waitFor(() => expect(pool.end).toHaveBeenCalledTimes(1));
    await nextTurn();
    expect(exit).not.toHaveBeenCalled();

    pool.finish();
    expect(await exited).toBe(0);
  });

  it("exits 1 at the deadline with requests still in flight, and only once", async () => {
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    const pool = fakePool();
    const { onSignal, exit, exited } = await start(pool, 50);

    const inFlight = [get("/slow?phone=5215512345678"), get("/slow/5215512345678")];
    await whenEntered(2);
    onSignal("SIGTERM");

    expect(await exited).toBe(1);
    expect(pool.end).not.toHaveBeenCalled();
    // Names what it cut off, without query strings or a path that could hold PII.
    const line = String(errors.mock.calls[0]?.[0]);
    expect(line).toMatch(/Still in flight: /);
    expect(line).toContain("GET /slow");
    expect(line).toContain("GET (path withheld)");
    expect(line).not.toContain("5215512345678");

    // The drain still finishes behind the deadline; it doesn't exit a second time.
    release();
    await Promise.all(inFlight);
    await vi.waitFor(() => expect(pool.end).toHaveBeenCalledTimes(1));
    expect(exit).toHaveBeenCalledTimes(1);
  });

  it("exits 1 at the deadline while the pool is still closing, and only once", async () => {
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    const pool = stalledPool();
    const { onSignal, exit, exited } = await start(pool, 50);

    onSignal("SIGTERM");

    expect(await exited).toBe(1);
    expect(errors).toHaveBeenCalledWith(expect.stringMatching(/Still in flight: none$/));
    pool.finish();
    await nextTurn();
    expect(exit).toHaveBeenCalledTimes(1);
  });

  it("exits 1 when closing the pool fails", async () => {
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    const failingPool = {
      end: async () => {
        throw new Error("boom");
      },
    };
    const { onSignal, exited } = await start(failingPool);

    onSignal("SIGTERM");

    expect(await exited).toBe(1);
    expect(errors).toHaveBeenCalledWith("Postgres pool close error:", expect.any(Error));
  });

  it("still closes the pool, and exits 1, when the server wasn't running", async () => {
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    const pool = fakePool();
    const { onSignal, exited } = await start(pool);
    await new Promise((resolve) => server.close(resolve));

    onSignal("SIGTERM");

    expect(await exited).toBe(1);
    expect(errors).toHaveBeenCalledWith(
      "HTTP server close error:",
      expect.objectContaining({ code: "ERR_SERVER_NOT_RUNNING" }),
    );
    expect(pool.end).toHaveBeenCalledTimes(1);
  });

  it("closes an idle keep-alive socket instead of waiting out keepAliveTimeout", async () => {
    const { onSignal, exited } = await start(fakePool(), 5_000);
    server.keepAliveTimeout = 60_000;

    expect(await get("/fast")).toMatchObject({ status: 200, connection: "keep-alive" });
    onSignal("SIGTERM");

    expect(await exited).toBe(0);
  });

  it("serves a request that arrives mid-drain, with Connection: close", async () => {
    const { onSignal, exited } = await start(fakePool(), 5_000);
    server.keepAliveTimeout = 60_000;

    // Half-sent headers keep the connection out of server.close()'s idle sweep.
    const accepted = new Promise<Socket>((resolve) => server.once("connection", resolve));
    const client = net.connect(port, "127.0.0.1");
    client.write("GET /fast HTTP/1.1\r\nHost: test\r\n");
    const serverSide = await accepted;
    await vi.waitFor(() => expect(serverSide.bytesRead).toBeGreaterThan(0));
    onSignal("SIGTERM");

    let raw = "";
    client.setEncoding("utf8").on("data", (chunk: string) => (raw += chunk));
    const closed = new Promise((resolve) => client.on("close", resolve));
    client.write("\r\n");

    expect(await exited).toBe(0);
    await closed;
    expect(raw).toMatch(/^HTTP\/1\.1 200 /);
    expect(raw).toMatch(/\r\nconnection: close\r\n/i);
  });
});
