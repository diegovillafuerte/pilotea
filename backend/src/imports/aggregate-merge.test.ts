/**
 * Pure unit tests for the canonical weekly_aggregates merge (PR-A). No DB / IO —
 * these lock the field-level coalesce semantics the import-strategy §0.5 review
 * required: preserve nulls, incoming-wins-where-present, recompute derived ratios
 * from the merged raw fields, 'mixed' provenance, and refuse a junk fresh row.
 */

import { describe, it, expect } from "vitest";
import {
  mergeAggregate,
  mergeSource,
  hasNoCoreEarnings,
  type AggregateColumns,
} from "./aggregate-merge.js";

function cols(overrides: Partial<AggregateColumns> = {}): AggregateColumns {
  return {
    netEarnings: null,
    grossEarnings: null,
    totalTrips: null,
    totalKm: null,
    hoursOnline: null,
    earningsPerTrip: null,
    earningsPerKm: null,
    earningsPerHour: null,
    tripsPerHour: null,
    platformCommissionPct: null,
    source: "imported",
    ...overrides,
  };
}

describe("mergeAggregate — fresh insert (no existing row)", () => {
  it("inserts incoming and recomputes derived ratios from its raw fields", () => {
    const res = mergeAggregate(
      cols({ netEarnings: "100.00", totalTrips: 4, totalKm: "50.00", hoursOnline: "2.00" }),
      null,
    );
    expect(res.kind).toBe("merged");
    if (res.kind !== "merged") return;
    expect(Number(res.values.earningsPerTrip)).toBe(25); // 100 / 4
    expect(Number(res.values.earningsPerKm)).toBe(2); // 100 / 50
    expect(Number(res.values.earningsPerHour)).toBe(50); // 100 / 2
    expect(Number(res.values.tripsPerHour)).toBe(2); // 4 / 2
    expect(res.values.source).toBe("imported");
  });

  it("refuses a fresh row carrying no core earnings (net/gross/trips all null)", () => {
    const res = mergeAggregate(cols({ totalKm: "50.00", platformCommissionPct: "20.00" }), null);
    expect(res.kind).toBe("rejected");
  });

  it("accepts a fresh row when at least one core field is present (gross only)", () => {
    const res = mergeAggregate(cols({ grossEarnings: "500.00" }), null);
    expect(res.kind).toBe("merged");
  });

  it("nulls a derived ratio whose denominator is missing or zero", () => {
    const res = mergeAggregate(cols({ netEarnings: "100.00", totalKm: "0.00", totalTrips: 0 }), null);
    expect(res.kind).toBe("merged");
    if (res.kind !== "merged") return;
    expect(res.values.earningsPerKm).toBeNull(); // km = 0 → no ratio
    expect(res.values.earningsPerTrip).toBeNull(); // trips = 0 → no ratio
  });
});

describe("mergeAggregate — merge onto an existing row", () => {
  it("preserves an existing field the incoming source omits (no clobber)", () => {
    const existing = cols({ netEarnings: "100.00", totalTrips: 4, totalKm: "50.00", source: "captured" });
    const incoming = cols({ netEarnings: "200.00", totalTrips: 8, source: "imported" }); // no km
    const res = mergeAggregate(incoming, existing);
    expect(res.kind).toBe("merged");
    if (res.kind !== "merged") return;
    expect(Number(res.values.netEarnings)).toBe(200); // incoming wins
    expect(res.values.totalTrips).toBe(8); // incoming wins
    expect(Number(res.values.totalKm)).toBe(50); // preserved from existing
    expect(Number(res.values.earningsPerKm)).toBe(4); // recomputed: merged net 200 / preserved km 50
    expect(res.values.source).toBe("mixed"); // captured + imported
  });

  it("a null incoming field keeps the existing value (COALESCE)", () => {
    const existing = cols({ netEarnings: "100.00", platformCommissionPct: "18.00", source: "imported" });
    const incoming = cols({ netEarnings: "150.00", source: "captured" }); // no commission
    const res = mergeAggregate(incoming, existing);
    if (res.kind !== "merged") throw new Error("expected merged");
    expect(Number(res.values.netEarnings)).toBe(150);
    expect(Number(res.values.platformCommissionPct)).toBe(18); // imported commission preserved
    expect(res.values.source).toBe("mixed");
  });
});

describe("mergeSource", () => {
  it("captured + imported → mixed", () => {
    expect(mergeSource("captured", "imported")).toBe("mixed");
    expect(mergeSource("imported", "captured")).toBe("mixed");
  });
  it("same source is preserved", () => {
    expect(mergeSource("imported", "imported")).toBe("imported");
    expect(mergeSource("captured", "captured")).toBe("captured");
  });
  it("mixed is absorbing", () => {
    expect(mergeSource("mixed", "imported")).toBe("mixed");
    expect(mergeSource("imported", "mixed")).toBe("mixed");
  });
});

describe("hasNoCoreEarnings", () => {
  it("true only when net, gross AND trips are all null", () => {
    expect(hasNoCoreEarnings({ netEarnings: null, grossEarnings: null, totalTrips: null })).toBe(true);
    expect(hasNoCoreEarnings({ netEarnings: "1", grossEarnings: null, totalTrips: null })).toBe(false);
    expect(hasNoCoreEarnings({ netEarnings: null, grossEarnings: null, totalTrips: 0 })).toBe(false);
  });
});
