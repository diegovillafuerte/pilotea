CREATE TABLE "premium_grants" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid NOT NULL,
	"days" integer NOT NULL,
	"reason" varchar(40) NOT NULL,
	"source_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "referral_codes" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"driver_id" uuid,
	"code" varchar(16) NOT NULL,
	"type" varchar(20) DEFAULT 'driver' NOT NULL,
	"name" varchar(100),
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "referral_codes_driver_id_unique" UNIQUE("driver_id"),
	CONSTRAINT "referral_codes_code_unique" UNIQUE("code")
);
--> statement-breakpoint
CREATE TABLE "referral_redemptions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"code_id" uuid NOT NULL,
	"redeemer_driver_id" uuid NOT NULL,
	"redeemer_device_id" uuid NOT NULL,
	"granted_days_referrer" integer DEFAULT 0 NOT NULL,
	"granted_days_redeemer" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "referral_redemptions_redeemer_driver_id_unique" UNIQUE("redeemer_driver_id"),
	CONSTRAINT "referral_redemptions_redeemer_device_id_unique" UNIQUE("redeemer_device_id")
);
--> statement-breakpoint
ALTER TABLE "drivers" ADD COLUMN "premium_until" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "premium_grants" ADD CONSTRAINT "premium_grants_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "referral_codes" ADD CONSTRAINT "referral_codes_driver_id_drivers_id_fk" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "referral_redemptions" ADD CONSTRAINT "referral_redemptions_code_id_referral_codes_id_fk" FOREIGN KEY ("code_id") REFERENCES "public"."referral_codes"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "referral_redemptions" ADD CONSTRAINT "referral_redemptions_redeemer_driver_id_drivers_id_fk" FOREIGN KEY ("redeemer_driver_id") REFERENCES "public"."drivers"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_premium_grants_driver_created" ON "premium_grants" USING btree ("driver_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_referral_codes_type" ON "referral_codes" USING btree ("type");--> statement-breakpoint
CREATE INDEX "idx_referral_redemptions_code_created" ON "referral_redemptions" USING btree ("code_id","created_at");