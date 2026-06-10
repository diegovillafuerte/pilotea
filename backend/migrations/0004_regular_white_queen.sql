CREATE TABLE "fixture_reports" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"device_id" uuid NOT NULL,
	"host_package" varchar(255) NOT NULL,
	"host_version" varchar(100),
	"spec_version" integer,
	"reason" varchar(40),
	"snapshot" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE INDEX "idx_fixture_reports_device_created" ON "fixture_reports" USING btree ("device_id","created_at");