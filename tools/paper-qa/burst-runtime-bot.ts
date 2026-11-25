// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
import { Client } from "mc-protocol";

const client = new Client({
  host: "127.0.0.1",
  port: 25575,
});

let joined = false;
let failed = false;

const timeout = setTimeout(() => {
  console.error("RASKOL runtime QA bot timed out before joining Paper 26.2.");
  process.exit(1);
}, 120_000);

client.on("Socket:Ready", async () => {
  try {
    await client.sendPacket("Intension", {
      protocolVersion: 776,
      serverHost: "127.0.0.1",
      serverPort: 25575,
      nextState: 2,
    });
    await client.sendPacket("Hello", {
      username: "RaskolRuntimeQA",
      playerUUID: "a81b9f5e4d2c4a6f8b0c1d2e3f4a5b6c",
    });
    console.log("RASKOL runtime QA bot sent the 26.2 offline login handshake.");
  } catch (error) {
    failed = true;
    console.error("RASKOL runtime QA bot handshake failed:", error);
    process.exit(1);
  }
});

client.on("Login/LoginFinished", async () => {
  try {
    await client.sendPacket("LoginAcknowledged");
  } catch (error) {
    failed = true;
    console.error("RASKOL runtime QA bot login acknowledgement failed:", error);
    process.exit(1);
  }
});

client.on("Config/SelectKnownPacks", async ({ knownPacks }) => {
  try {
    await client.sendPacket("SelectKnownPacks", { knownPacks });
  } catch (error) {
    failed = true;
    console.error("RASKOL runtime QA bot known-pack negotiation failed:", error);
    process.exit(1);
  }
});

client.on("Config/FinishConfiguration", async () => {
  try {
    await client.sendPacket("FinishConfiguration");
  } catch (error) {
    failed = true;
    console.error("RASKOL runtime QA bot configuration finish failed:", error);
    process.exit(1);
  }
});

client.on("playerJoin", (session) => {
  joined = true;
  clearTimeout(timeout);
  console.log("RASKOL runtime QA bot joined Paper 26.2:", session.username);
});

client.on("Client:Error", (error) => {
  failed = true;
  console.error("RASKOL runtime QA bot protocol error:", error);
  process.exit(1);
});

client.on("Socket:Close", () => {
  if (!joined && !failed) {
    clearTimeout(timeout);
    console.error("RASKOL runtime QA bot disconnected before joining Paper 26.2.");
    process.exit(1);
  }
});
