import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { getDb } from "./db/client.js";

const port = Number(process.env.PORT ?? 8080);

const app = createApp(getDb());

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`kompara-backend listening on http://localhost:${info.port}`);
});
