// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
import mc from "minecraft-protocol";

const client = mc.createClient({
  host: "127.0.0.1",
  port: 25575,
  username: "RaskolPathAQA",
  auth: "offline",
  version: "26.2",
});

let joined = false;
let failed = false;

const timeout = setTimeout(() => {
  failed = true;
  console.error("RASKOL Path A QA bot timed out before joining Paper 26.2.");
  client.end("RASKOL Path A QA timeout");
  process.exit(1);
}, 120_000);

client.once("playerJoin", () => {
  joined = true;
  clearTimeout(timeout);
  console.log("RASKOL Path A QA bot joined Paper 26.2 (protocol 776):", client.username);
});

client.on("error", (error) => {
  failed = true;
  clearTimeout(timeout);
  console.error("RASKOL Path A QA bot protocol error:", error);
  process.exit(1);
});

client.on("end", (reason) => {
  if (!joined && !failed) {
    failed = true;
    clearTimeout(timeout);
    console.error("RASKOL Path A QA bot disconnected before joining Paper 26.2:", reason);
    process.exit(1);
  }
});
