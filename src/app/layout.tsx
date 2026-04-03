import type { Metadata, Viewport } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-geist-sans",
  subsets: ["latin"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Kompara",
    template: "%s | Kompara",
  },
  description:
    "Compara tus ganancias como conductor de plataforma. Sube tus capturas, ve percentiles y descubre cuanto realmente ganas.",
  keywords: ["uber", "didi", "indriver", "conductor", "ganancias", "mexico"],
  openGraph: {
    title: "Kompara",
    description: "Compara tus ganancias como conductor de plataforma",
    siteName: "Kompara",
    locale: "es_MX",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#059669",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="es"
      className={`${inter.variable} ${jetbrainsMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
