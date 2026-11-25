// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
const minecraft = require("minecraft-protocol");

const client = minecraft.createClient({
  host: "127.0.0.1",
  port: 25575,
  username: "RaskolRuntimeQA",
  auth: "offline",
});

let spawned = false;
const timeout = setTimeout(() => {
  console.error("RASKOL runtime QA bot timed out before the matrix completed.");
  client.end("runtime QA timeout");
  process.exitCode = 1;
}, 240_000);

client.on("login", () => {
  console.log("RASKOL runtime QA bot authenticated.");
});

client.on("spawn", () => {
  spawned = true;
  console.log("RASKOL runtime QA bot spawned; waiting for server-side matrix.");
});

client.on("end", () => {
  clearTimeout(timeout);
  if (!spawned) {
    console.error("RASKOL runtime QA bot disconnected before spawn.");
    process.exitCode = 1;
  }
});

client.on("error", (error) => {
  console.error("RASKOL runtime QA bot error:", error.message);
});
