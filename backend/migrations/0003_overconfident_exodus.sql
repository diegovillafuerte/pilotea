CREATE TABLE "imports" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"platform" varchar(20) NOT NULL,
	"upload_type" varchar(20) NOT NULL,
	"file_key" text NOT NULL,
	"status" varchar(20) DEFAULT 'pending' NOT NULL,
	"error_message" text,
	"parsed_payload" jsonb,
	"weekly_aggregate_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "imports" ADD CONSTRAINT "imports_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "imports" ADD CONSTRAINT "imports_weekly_aggregate_id_weekly_aggregates_id_fk" FOREIGN KEY ("weekly_aggregate_id") REFERENCES "public"."weekly_aggregates"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_imports_driver_created" ON "imports" USING btree ("driver_id","created_at");