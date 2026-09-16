#!/usr/bin/env node
// Turns the React Native app's debug log into the JSONL the Pack's events.mjs reads (review A4, A6).
//
//   node rn-events.mjs <log file> [--out rn-events.jsonl] [--quiet]
//
// The RN app has no file sink for analytics. `ax.metric(name, payload)` reaches `MetricsClient.track`
// (src/analytics/metrics/client.ts), which calls
//
//     logger.debug(`event: ${e.event}`, e)     // e = {source, time, event, payload, metadata}
//
// on a logger whose context is `metric`. In a development build the console transport
// (src/logger/transports/console.ts) prints
//
//     <HH:mm:ss> (metric) event: <name> <JSON.stringify(e, null, 2)>
//
// with ANSI colour around the timestamp and the context, as one `console.log` string. That reaches the Metro
// dev-server output and, when the app logs to the device, the `ReactNativeJS` logcat tag. This reads either.
//
// Debug logs are filtered by context: the RN app's local `.env` must list `metric` in EXPO_PUBLIC_LOG_DEBUG
// (`Logger.create` -> `contextFilter`), and the Expo dev server inlines that at start, so Metro has to be
// restarted after changing it. PARITY.md records this as a capture step.
//
// Output: one {"name","props"} object per line, in the order the app logged them. `props` is the event's
// `payload`, which is what the native app's JSONL sink writes too.
import fs from "node:fs";

const args = process.argv.slice(2);
const file = args.find((a) => !a.startsWith("--"));
const outIdx = args.indexOf("--out");
const out = outIdx === -1 ? null : args[outIdx + 1];
const quiet = args.includes("--quiet");
if (!file) {
  process.stderr.write("Usage: node rn-events.mjs <log file> [--out rn-events.jsonl] [--quiet]\n");
  process.exit(2);
}

const ANSI = /\x1B\[[0-9;]*m/g;
// `09-16 14:23:01.123  4242  4242 I ReactNativeJS: ` and similar logcat prefixes.
const LOGCAT_PREFIX = /^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+\s+[VDIWEF]\s+[^:]+:\s?/;

const text = fs
  .readFileSync(file, "utf8")
  .replace(ANSI, "")
  .split(/\r?\n/)
  .map((line) => line.replace(LOGCAT_PREFIX, ""))
  .join("\n");

/** Reads one JSON object starting at `start` (which must be a `{`), honouring strings and escapes. */
function readObject(source, start) {
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < source.length; i++) {
    const ch = source[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    else if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) return { json: source.slice(start, i + 1), end: i + 1 };
    }
  }
  return null;
}

const MARKER = /\(metric\)\s*event:\s*(\S+)/g;
const events = [];
const problems = [];
let match;
while ((match = MARKER.exec(text)) !== null) {
  const name = match[1];
  // The pretty-printed event object follows the message, when the event carried one.
  const rest = text.slice(match.index + match[0].length);
  const braceOffset = rest.indexOf("{");
  const newlineOffset = rest.indexOf("\n");
  let props = {};
  let eventName = name;
  // A `{` that comes after the next marker, or far past the message, is a different log line.
  if (braceOffset !== -1 && (newlineOffset === -1 || braceOffset < newlineOffset + 2)) {
    const read = readObject(rest, braceOffset);
    if (read) {
      try {
        const parsed = JSON.parse(read.json);
        if (typeof parsed.event === "string") eventName = parsed.event;
        if (parsed.payload && typeof parsed.payload === "object" && !Array.isArray(parsed.payload)) {
          props = parsed.payload;
        }
      } catch (e) {
        problems.push(`${name}: could not parse the event object (${e.message})`);
      }
    }
  }
  events.push({ name: eventName, props });
}

const jsonl = events.map((e) => JSON.stringify(e)).join("\n") + (events.length ? "\n" : "");
if (out) fs.writeFileSync(out, jsonl);
else process.stdout.write(jsonl);

if (!quiet) {
  const where = out ? ` -> ${out}` : "";
  process.stderr.write(`${events.length} RN event(s) from ${file}${where}\n`);
  for (const p of problems) process.stderr.write(`  warning: ${p}\n`);
  if (!events.length) {
    process.stderr.write(
      "  No `(metric)` lines. Check that EXPO_PUBLIC_LOG_DEBUG lists `metric` in the RN app's .env and that\n" +
        "  Metro was restarted afterwards, and that this log covers a run where the app actually rendered.\n",
    );
  }
}
process.exit(events.length ? 0 : 1);
