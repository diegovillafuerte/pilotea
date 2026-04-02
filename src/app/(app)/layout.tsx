export default function AppLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex-1">{children}</main>
      {/* TODO: Bottom nav - Dashboard | Comparar | Subir | Fiscal | Tips */}
    </div>
  );
}
