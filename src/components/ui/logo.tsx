import Image from "next/image";

type LogoVariant = "full" | "monogram";
type LogoTheme = "default" | "white";

interface LogoProps {
  variant?: LogoVariant;
  theme?: LogoTheme;
  className?: string;
  size?: number;
}

export function Logo({
  variant = "full",
  theme = "default",
  className,
  size,
}: LogoProps) {
  if (variant === "monogram") {
    const src =
      theme === "white" ? "/logo-monogram-white.svg" : "/logo-monogram.svg";
    const s = size ?? 40;
    return (
      <Image
        src={src}
        alt="Kompara"
        width={s}
        height={s}
        className={className}
        priority
      />
    );
  }

  const h = size ?? 40;
  const w = Math.round(h * (200 / 48));
  return (
    <Image
      src="/logo-full.svg"
      alt="Kompara"
      width={w}
      height={h}
      className={className}
      priority
    />
  );
}
