CREATE TABLE "drivers" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"phone" varchar(20) NOT NULL,
	"name" varchar(100),
	"city" varchar(100),
	"platforms" text[],
	"tier" varchar(20) DEFAULT 'free' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "drivers_phone_unique" UNIQUE("phone")
);
--> statement-breakpoint
CREATE TABLE "parser_configs" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"target_package" varchar(255) NOT NULL,
	"version_range" varchar(100) NOT NULL,
	"spec_version" integer NOT NULL,
	"spec" jsonb NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
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
	"is_synthetic" boolean DEFAULT false NOT NULL,
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
CREATE TABLE "telemetry_counters" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"host_package" varchar(255) NOT NULL,
	"host_version" varchar(100) NOT NULL,
	"spec_version" integer NOT NULL,
	"attempts" integer DEFAULT 0 NOT NULL,
	"successes" integer DEFAULT 0 NOT NULL,
	"failures" integer DEFAULT 0 NOT NULL,
	"day" date NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "weekly_aggregates" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"platform" varchar(20) NOT NULL,
	"week_start" date NOT NULL,
	"net_earnings" numeric(10, 2) NOT NULL,
	"gross_earnings" numeric(10, 2) NOT NULL,
	"total_trips" integer NOT NULL,
	"total_km" numeric(10, 2),
	"hours_online" numeric(10, 2),
	"earnings_per_trip" numeric(10, 2),
	"earnings_per_km" numeric(10, 2),
	"earnings_per_hour" numeric(10, 2),
	"trips_per_hour" numeric(10, 2),
	"platform_commission_pct" numeric(5, 2),
	"source" varchar(20) DEFAULT 'captured' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "weekly_aggregates" ADD CONSTRAINT "weekly_aggregates_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "uq_parser_configs_pkg_range_spec" ON "parser_configs" USING btree ("target_package","version_range","spec_version");--> statement-breakpoint
CREATE INDEX "idx_parser_configs_pkg_active" ON "parser_configs" USING btree ("target_package","active");--> statement-breakpoint
CREATE UNIQUE INDEX "uq_population_stats_city_platform_metric_period" ON "population_stats" USING btree ("city","platform","metric_name","period");--> statement-breakpoint
CREATE INDEX "idx_sessions_token_hash" ON "sessions" USING btree ("token_hash");--> statement-breakpoint
CREATE UNIQUE INDEX "uq_telemetry_counters_pkg_version_spec_day" ON "telemetry_counters" USING btree ("host_package","host_version","spec_version","day");--> statement-breakpoint
CREATE UNIQUE INDEX "uq_weekly_aggregates_driver_platform_week" ON "weekly_aggregates" USING btree ("driver_id","platform","week_start");--> statement-breakpoint
CREATE INDEX "idx_weekly_aggregates_driver" ON "weekly_aggregates" USING btree ("driver_id","week_start");