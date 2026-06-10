/**
 * Outbound WhatsApp OTP delivery, behind a small interface so the transport is
 * swappable and testable.
 *
 * - {@link TwilioWhatsAppSender} — production transport, configured from env.
 *   Ported from the legacy web client (src/lib/whatsapp/client.ts): same Twilio
 *   REST shape, but sends a 6-digit OTP instead of a magic link (OTP fits a
 *   native Android flow better than a tappable link).
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
    // eslint-disable-next-line no-console
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
  ) {}

  async sendOtp(phone: string, code: string): Promise<void> {
    const url = `https://api.twilio.com/2010-04-01/Accounts/${this.accountSid}/Messages.json`;
    const params = new URLSearchParams({
      From: this.fromNumber,
      To: phone.startsWith("whatsapp:") ? phone : `whatsapp:${phone}`,
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
 * shared sandbox sender, the same default the web client used.
 */
export function senderFromEnv(env: NodeJS.ProcessEnv = process.env): MessageSender {
  const sid = env.TWILIO_ACCOUNT_SID;
  const token = env.TWILIO_AUTH_TOKEN;
  if (sid && token) {
    const from = env.TWILIO_WHATSAPP_FROM ?? "whatsapp:+14155238886";
    return new TwilioWhatsAppSender(sid, token, from);
  }
  return new DevLogSender();
}
