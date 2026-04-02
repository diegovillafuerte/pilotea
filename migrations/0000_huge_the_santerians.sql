CREATE TABLE "drivers" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"phone" varchar(20) NOT NULL,
	"name" varchar(100),
	"city" varchar(100),
	"platforms" text[],
	"tier" varchar(20) DEFAULT 'free' NOT NULL,
	"onboarding_completed" boolean DEFAULT false NOT NULL,
	"streak_weeks" integer DEFAULT 0 NOT NULL,
	"last_upload_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "drivers_phone_unique" UNIQUE("phone")
);
--> statement-breakpoint
CREATE TABLE "magic_links" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"phone" varchar(20) NOT NULL,
	"token" varchar(64) NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"used_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "magic_links_token_unique" UNIQUE("token")
);
--> statement-breakpoint
CREATE TABLE "population_stats" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"city" varchar(100) NOT NULL,
	"platform" varchar(20) NOT NULL,
	"metric_name" varchar(50) NOT NULL,
	"period" varchar(20) NOT NULL,
	"sample_size" integer NOT NULL,
	"p10" numeric(10, 2) NOT NULL,
	"p25" numeric(10, 2) NOT NULL,
	"p50" numeric(10, 2) NOT NULL,
	"p75" numeric(10, 2) NOT NULL,
	"p90" numeric(10, 2) NOT NULL,
	"mean" numeric(10, 2) NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"token_hash" varchar(64) NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "subscriptions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"tier" varchar(20) NOT NULL,
	"status" varchar(20) DEFAULT 'active' NOT NULL,
	"amount" numeric(10, 2) NOT NULL,
	"started_at" timestamp with time zone NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"payment_reference" varchar(255),
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "uploads" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"platform" varchar(20) NOT NULL,
	"upload_type" varchar(20) NOT NULL,
	"file_key" text NOT NULL,
	"status" varchar(20) DEFAULT 'pending' NOT NULL,
	"error_message" text,
	"parsed_data" jsonb,
	"weekly_data_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "weekly_data" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"platform" varchar(20) NOT NULL,
	"week_start" date NOT NULL,
	"net_earnings" numeric(10, 2) NOT NULL,
	"gross_earnings" numeric(10, 2) NOT NULL,
	"total_trips" integer NOT NULL,
	"earnings_per_trip" numeric(10, 2),
	"earnings_per_km" numeric(10, 2),
	"earnings_per_hour" numeric(10, 2),
	"trips_per_hour" numeric(10, 2),
	"platform_commission_pct" numeric(5, 2),
	"total_km" numeric(10, 2),
	"hours_online" numeric(10, 2),
	"platform_commission" numeric(10, 2),
	"taxes" numeric(10, 2),
	"incentives" numeric(10, 2),
	"tips" numeric(10, 2),
	"surge_earnings" numeric(10, 2),
	"wait_time_earnings" numeric(10, 2),
	"active_days" integer,
	"peak_day_earnings" numeric(10, 2),
	"peak_day_name" varchar(20),
	"cash_amount" numeric(10, 2),
	"card_amount" numeric(10, 2),
	"rewards" numeric(10, 2),
	"data_completeness" numeric(3, 2),
	"raw_extraction" jsonb,
	"upload_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "subscriptions" ADD CONSTRAINT "subscriptions_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "uploads" ADD CONSTRAINT "uploads_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "weekly_data" ADD CONSTRAINT "weekly_data_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "weekly_data" ADD CONSTRAINT "weekly_data_upload_id_uploads_id_fk" FOREIGN KEY ("upload_id") REFERENCES "public"."uploads"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_magic_links_token" ON "magic_links" USING btree ("token");--> statement-breakpoint
CREATE UNIQUE INDEX "uq_population_stats_city_platform_metric_period" ON "population_stats" USING btree ("city","platform","metric_name","period");--> statement-breakpoint
CREATE INDEX "idx_sessions_token_hash" ON "sessions" USING btree ("token_hash");--> statement-breakpoint
CREATE INDEX "idx_uploads_driver_status" ON "uploads" USING btree ("driver_id","status");--> statement-breakpoint
CREATE UNIQUE INDEX "uq_weekly_data_driver_platform_week" ON "weekly_data" USING btree ("driver_id","platform","week_start");--> statement-breakpoint
CREATE INDEX "idx_weekly_data_driver" ON "weekly_data" USING btree ("driver_id","week_start");--> statement-breakpoint
CREATE INDEX "idx_weekly_data_city_platform" ON "weekly_data" USING btree ("platform","week_start");