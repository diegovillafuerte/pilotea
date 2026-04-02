import twilio from "twilio";

const accountSid = process.env.TWILIO_ACCOUNT_SID;
const authToken = process.env.TWILIO_AUTH_TOKEN;
const fromNumber = process.env.TWILIO_WHATSAPP_FROM ?? "whatsapp:+14155238886";
function getMagicLinkBaseUrl(): string {
  const url = process.env.MAGIC_LINK_BASE_URL;
  if (!url) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("MAGIC_LINK_BASE_URL is required in production");
    }
    return "http://localhost:3000";
  }
  if (process.env.NODE_ENV === "production" && !url.startsWith("https://")) {
    throw new Error("MAGIC_LINK_BASE_URL must use HTTPS in production");
  }
  return url;
}

function getTwilioClient() {
  if (!accountSid || !authToken) {
    throw new Error(
      "TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN environment variables are required",
    );
  }
  return twilio(accountSid, authToken);
}

/**
 * Send a magic link to the driver's WhatsApp using the pre-approved template.
 *
 * Template: auth_magic_link (es_MX)
 * Body: "Hola {{1}}! Entra a Kompara con este link: {{2}}
 *        Expira en 15 minutos. Si no solicitaste esto, ignora este mensaje."
 */
export async function sendMagicLink(
  phone: string,
  token: string,
): Promise<void> {
  const client = getTwilioClient();
  const baseUrl = getMagicLinkBaseUrl();
  const verifyUrl = `${baseUrl}/api/auth/verify?token=${token}`;

  await client.messages.create({
    from: fromNumber,
    to: `whatsapp:${phone}`,
    contentSid: process.env.TWILIO_TEMPLATE_SID,
    contentVariables: JSON.stringify({
      "1": "conductor",
      "2": verifyUrl,
    }),
    // Fallback body for sandbox/testing (when no template SID is set)
    ...(process.env.TWILIO_TEMPLATE_SID
      ? {}
      : {
          body: `Hola! Entra a Kompara con este link:\n\n${verifyUrl}\n\nExpira en 15 minutos. Si no solicitaste esto, ignora este mensaje.`,
        }),
  });
}
