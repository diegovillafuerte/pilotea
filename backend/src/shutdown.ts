/**
 * Graceful shutdown on SIGTERM/SIGINT (techdebt TD-046 item 6).
 *
 * Render SIGTERMs the old instance on every deploy. Without a handler Node
 * exits on the spot and every in-flight request dies with it; for
 * POST /v1/imports, dying between the R2 upload and the row insert orphans the
 * stored originals. The drain stops accepting connections, waits for every
 * connection to end and every handler to finish, closes the Postgres pool,
 * then exits 0.
 */

import type { Server, ServerResponse } from "node:http";

/**
 * Render SIGKILLs an instance that is still running 30 s after SIGTERM (its
 * default shutdown delay; render.yaml sets no `maxShutdownDelaySeconds`).
 * Give up a few seconds earlier, so a stalled request (R2 calls have no
 * timeout yet: TD-046 item 5) ends in a logged exit 1, not a silent SIGKILL.
 */
const DEADLINE_MS = 25_000;

interface Pool {
  end(): Promise<void>;
}

interface ShutdownOptions {
  deadlineMs?: number;
  exit?: (code: number) => void;
}

type Fetch<A extends unknown[]> = (request: Request, ...rest: A) => Response | Promise<Response>;

/**
 * Wire the drain in two steps: `track` wraps the app's fetch (pass the result
 * to `serve()`), then `attach` watches the server `serve()` returns and builds
 * the SIGTERM/SIGINT handler. A repeated signal only logs; it neither restarts
 * the drain nor kills the process.
 */
export function gracefulShutdown(
  pool: Pool,
  { deadlineMs = DEADLINE_MS, exit = (code) => process.exit(code) }: ShutdownOptions = {},
) {
  // A handler outlives its socket when the client hangs up mid-request (the
  // adapter aborts the Request's signal, which no route observes), so the drain
  // waits for these promises as well as for connections to end. It can't see
  // work a route leaves running after its promise settles (a Promise.all
  // sibling after the first rejection, say), which can race the pool closing:
  // routes should await everything they start.
  const handlers = new Map<Promise<Response>, Request>();
  const responses = new Set<ServerResponse>();
  let draining = false;

  function track<A extends unknown[]>(fetch: Fetch<A>): Fetch<A> {
    return (request, ...rest) => {
      const result = fetch(request, ...rest);
      if (result instanceof Promise) {
        handlers.set(result, request);
        const settled = () => handlers.delete(result);
        result.then(settled, settled);
      }
      return result;
    };
  }

  function attach(server: Server): (signal: NodeJS.Signals) => void {
    // Prepended so it runs before the Hono adapter, which can write a response
    // synchronously. While draining, `Connection: close` makes Node end each
    // keep-alive socket right after its response: server.close() only closes
    // the sockets that are idle when it's called, and waits out
    // keepAliveTimeout on the rest.
    server.prependListener("request", (_req, res) => {
      responses.add(res);
      res.once("close", () => responses.delete(res));
      if (draining) res.setHeader("connection", "close");
    });

    return (signal) => {
      if (draining) {
        console.log(`${signal} received again; already draining`);
        return;
      }
      draining = true;
      const startedAt = Date.now();
      console.log(`${signal} received: draining ${handlers.size} in-flight request(s)`);
      for (const res of responses) {
        if (!res.headersSent) res.setHeader("connection", "close");
      }

      let timer: NodeJS.Timeout | undefined;
      const deadline = new Promise<number>((resolve) => {
        timer = setTimeout(() => {
          const open = [...handlers.values()].map(describe).join(", ") || "none";
          console.error(`Shutdown deadline (${deadlineMs} ms) passed; exiting 1. Still in flight: ${open}`);
          resolve(1);
        }, deadlineMs);
      });

      // The race settles once, so `exit` runs once even if the drain finishes
      // after the deadline.
      void Promise.race([drain(server, handlers, pool), deadline]).then((code) => {
        clearTimeout(timer);
        if (code === 0) console.log(`Drained in ${Date.now() - startedAt} ms; exiting 0`);
        exit(code);
      });
    };
  }

  return { track, attach };
}

/**
 * Close the server (resolves once every connection has ended), wait for any
 * handler still running, then close the pool. No request can start once the
 * server has closed, so that snapshot of handlers is complete.
 */
async function drain(
  server: Server,
  handlers: Map<Promise<Response>, Request>,
  pool: Pool,
): Promise<number> {
  const closeErr = await new Promise<Error | undefined>((resolve) => server.close(resolve));
  if (closeErr) console.error("HTTP server close error:", closeErr);
  await Promise.allSettled(handlers.keys());
  try {
    await pool.end();
  } catch (err) {
    console.error("Postgres pool close error:", err);
    return 1;
  }
  return closeErr ? 1 : 0;
}

/**
 * "METHOD /path" for the deadline log. Every route's path is lowercase words
 * under /v1, so a path with anything else (an id, a phone number) is withheld.
 */
function describe(request: Request): string {
  const path = new URL(request.url).pathname;
  return `${request.method} ${/^(\/(v1|[a-z-]+))+$/.test(path) ? path : "(path withheld)"}`;
}
