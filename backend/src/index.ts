import type { Server } from "node:http";
import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { getDb } from "./db/client.js";
import { gracefulShutdown } from "./shutdown.js";

const port = Number(process.env.PORT ?? 8080);

const db = getDb();
const app = createApp(db);
const shutdown = gracefulShutdown(db.$client);

// serve() builds a node:http Server unless it is given a createServer option.
const server = serve({ fetch: shutdown.track(app.fetch), port }, (info) => {
  console.log(`kompara-backend listening on http://localhost:${info.port}`);
}) as Server;

const onSignal = shutdown.attach(server);
process.on("SIGTERM", onSignal);
process.on("SIGINT", onSignal);
