/**
 * Tests for the WhatsApp OTP transport selection + Twilio request shape.
 *
 * The production-critical bit: a configured TWILIO_TEMPLATE_SID must produce a
 * template send (ContentSid + ContentVariables, NO Body) — a freeform Body to a
 * recipient outside the 24h window fails with Twilio error 63016. Without a
 * template the sender falls back to a Body (sandbox / opted-in testers only).
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { DevLogSender, TwilioWhatsAppSender, senderFromEnv } from "./message-sender.js";

/** Stub global fetch with a 200 OK and return the mock for assertions. */
function mockFetchOk() {
  const fetchMock = vi.fn(async () => ({ ok: true, status: 200, text: async () => "" }) as any);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/** The URLSearchParams body of the first recorded fetch call. */
function sentParams(fetchMock: ReturnType<typeof vi.fn>): URLSearchParams {
  const call = fetchMock.mock.calls[0] as unknown as [string, { body: URLSearchParams }];
  return call[1].body;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("TwilioWhatsAppSender", () => {
  it("sends an approved template (ContentSid + ContentVariables, no Body) when a template SID is set", async () => {
    const fetchMock = mockFetchOk();
    const sender = new TwilioWhatsAppSender("AC123", "tok", "whatsapp:+14155238886", "HXtemplate");

    await sender.sendOtp("+5215512345678", "903557");

    const params = sentParams(fetchMock);
    expect(params.get("ContentSid")).toBe("HXtemplate");
    expect(params.get("ContentVariables")).toBe(JSON.stringify({ "1": "903557" }));
    expect(params.get("Body")).toBeNull();
    expect(params.get("To")).toBe("whatsapp:+5215512345678");
  });

  it("falls back to a freeform Body (no ContentSid) when no template SID is set", async () => {
    const fetchMock = mockFetchOk();
    const sender = new TwilioWhatsAppSender("AC123", "tok", "whatsapp:+14155238886");

    await sender.sendOtp("+5215512345678", "903557");

    const params = sentParams(fetchMock);
    expect(params.get("ContentSid")).toBeNull();
    expect(params.get("Body")).toContain("903557");
    expect(params.get("To")).toBe("whatsapp:+5215512345678");
  });

  it("does not double-prefix an already-whatsapp: recipient", async () => {
    const fetchMock = mockFetchOk();
    const sender = new TwilioWhatsAppSender("AC123", "tok", "whatsapp:+1", "HX");

    await sender.sendOtp("whatsapp:+5215512345678", "111111");

    expect(sentParams(fetchMock).get("To")).toBe("whatsapp:+5215512345678");
  });

  it("throws on a non-2xx Twilio response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: false, status: 400, text: async () => "bad" }) as any),
    );
    const sender = new TwilioWhatsAppSender("AC123", "tok", "whatsapp:+1", "HX");

    await expect(sender.sendOtp("+521", "123456")).rejects.toThrow(/Twilio send failed \(400\)/);
  });
});

describe("senderFromEnv", () => {
  it("returns the dev logger when Twilio creds are absent", () => {
    expect(senderFromEnv({} as NodeJS.ProcessEnv)).toBeInstanceOf(DevLogSender);
  });

  it("returns a Twilio sender that uses the configured template", async () => {
    const fetchMock = mockFetchOk();
    const sender = senderFromEnv({
      TWILIO_ACCOUNT_SID: "AC1",
      TWILIO_AUTH_TOKEN: "tok",
      TWILIO_WHATSAPP_FROM: "whatsapp:+1",
      TWILIO_TEMPLATE_SID: "HXabc",
    } as NodeJS.ProcessEnv);

    expect(sender).toBeInstanceOf(TwilioWhatsAppSender);
    await sender.sendOtp("+521", "654321");
    expect(sentParams(fetchMock).get("ContentSid")).toBe("HXabc");
  });
});
