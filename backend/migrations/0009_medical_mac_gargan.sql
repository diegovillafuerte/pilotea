ALTER TABLE "weekly_aggregates" ALTER COLUMN "net_earnings" DROP NOT NULL;--> statement-breakpoint
ALTER TABLE "weekly_aggregates" ALTER COLUMN "gross_earnings" DROP NOT NULL;--> statement-breakpoint
ALTER TABLE "weekly_aggregates" ALTER COLUMN "total_trips" DROP NOT NULL;