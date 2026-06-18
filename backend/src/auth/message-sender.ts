/**
 * Outbound WhatsApp OTP delivery, behind a small interface so the transport is
 * swappable and testable.
 *
 * - {@link TwilioWhatsAppSender} — production transport, configured from env.
 *   Ported from the legacy web client (src/lib/whatsapp/client.ts): same Twilio
 *   REST shape, but sends a 6-digit OTP instead of a magic link (OTP fits a
 *   native Android flow better than a tappable link). Production OTPs are
 *   business-initiated messages to a recipient outside the 24h session window,
 *   so they MUST use an approved WhatsApp template (TWILIO_TEMPLATE_SID); a
 *   freeform body fails with Twilio error 63016. The freeform body is kept only
 *   as a sandbox / opted-in-tester fallback (when no template is configured).
 * - {@link DevLogSender} — logs the OTP to the console. The default when no
 *   TWILIO_* env is present, so local dev and CI never need real credentials.
 *
 * Tests inject a fake implementation and assert on the captured code.
 */

export interface MessageSender {
  /** Send the 6-digit OTP `code` to `phone` (E.164, e.g. "+5215512345678"). */
  sendOtp(phone: string, code: string): Promise<void>;
}

/** Spanish (es_MX) OTP body, matching the legacy WhatsApp template voice. */
function otpBody(code: string): string {
  return `Tu codigo de Kompara es ${code}. Expira en 10 minutos. Si no lo solicitaste, ignora este mensaje.`;
}

/**
 * Logs the OTP instead of sending it. Used in dev/CI where there are no Twilio
 * credentials. The code is printed so a developer can complete the flow by hand.
 */
export class DevLogSender implements MessageSender {
  async sendOtp(phone: string, code: string): Promise<void> {
    console.log(`[DevLogSender] WhatsApp OTP for ${phone}: ${code}`);
  }
}

/**
 * Twilio WhatsApp transport. Calls the Twilio Messages REST API directly (no
 * SDK dependency) so the backend stays dependency-light. Constructed only when
 * TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN are present.
 */
export class TwilioWhatsAppSender implements MessageSender {
  constructor(
    private readonly accountSid: string,
    private readonly authToken: string,
    private readonly fromNumber: string,
    /**
     * Approved WhatsApp template (Authentication category) Content SID. When set, the OTP is sent
     * as a template (ContentSid + ContentVariables, no Body) — required for production business-
     * initiated delivery. When unset, falls back to a freeform Body that only delivers in the
     * Twilio sandbox / to opted-in testers (otherwise Twilio returns error 63016).
     */
    private readonly templateSid?: string,
  ) {}

  async sendOtp(phone: string, code: string): Promise<void> {
    const url = `https://api.twilio.com/2010-04-01/Accounts/${this.accountSid}/Messages.json`;
    const to = phone.startsWith("whatsapp:") ? phone : `whatsapp:${phone}`;
    // Template send (production) carries the code as variable {{1}} and omits Body; freeform send
    // (sandbox fallback) carries the human-readable Body. See error 63016 in the class doc.
    const params = this.templateSid
      ? new URLSearchParams({
          From: this.fromNumber,
          To: to,
          ContentSid: this.templateSid,
          ContentVariables: JSON.stringify({ "1": code }),
        })
      : new URLSearchParams({
          From: this.fromNumber,
          To: to,
          Body: otpBody(code),
        });
    const auth = Buffer.from(`${this.accountSid}:${this.authToken}`).toString("base64");

    const res = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: params,
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`Twilio send failed (${res.status}): ${text}`);
    }
  }
}

/**
 * Pick the message sender from the environment: Twilio when fully configured,
 * otherwise the dev logger. The WhatsApp "from" number defaults to Twilio's
 * shared sandbox sender, the same default the web client used. TWILIO_TEMPLATE_SID
 * (an approved Authentication template) is required for production delivery; when
 * unset the sender only works for the sandbox / opted-in testers (see error 63016).
 */
export function senderFromEnv(env: NodeJS.ProcessEnv = process.env): MessageSender {
  const sid = env.TWILIO_ACCOUNT_SID;
  const token = env.TWILIO_AUTH_TOKEN;
  if (sid && token) {
    const from = env.TWILIO_WHATSAPP_FROM ?? "whatsapp:+14155238886";
    const templateSid = env.TWILIO_TEMPLATE_SID;
    return new TwilioWhatsAppSender(sid, token, from, templateSid);
  }
  return new DevLogSender();
}
