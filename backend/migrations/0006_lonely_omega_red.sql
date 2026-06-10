CREATE TABLE "fiscal_config" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"year" integer NOT NULL,
	"minimum_wage_daily_mxn" numeric(10, 2) NOT NULL,
	"imss_monthly_threshold_mxn" numeric(10, 2) NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "fiscal_config_year_unique" UNIQUE("year")
);
