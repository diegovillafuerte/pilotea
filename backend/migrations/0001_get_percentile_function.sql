-- get_percentile(city, platform, metric, value) -> INTEGER
--
-- Ported VERBATIM from the legacy web app's proven implementation
-- (docs/technical-design.md §5.2). Calculates a driver's percentile (1-99) for
-- a metric using piecewise linear interpolation against population_stats.
-- Falls back to the 'national' row when the city-specific sample_size < 20.
-- Result is clamped to the 1..99 range.
--
-- The parity test (src/db/percentile.test.ts) asserts this returns identical
-- results to the web implementation for representative inputs.

CREATE OR REPLACE FUNCTION get_percentile(
  p_city VARCHAR,
  p_platform VARCHAR,
  p_metric VARCHAR,
  p_value DECIMAL
) RETURNS INTEGER AS $$
DECLARE
  stats RECORD;
BEGIN
  SELECT * INTO stats FROM population_stats
  WHERE city = p_city AND platform = p_platform
    AND metric_name = p_metric AND period = 'current';

  IF stats IS NULL OR stats.sample_size < 20 THEN
    SELECT * INTO stats FROM population_stats
    WHERE city = 'national' AND platform = p_platform
      AND metric_name = p_metric AND period = 'current';
  END IF;

  IF stats IS NULL THEN RETURN NULL; END IF;

  RETURN GREATEST(1, LEAST(99,
    CASE
      WHEN p_value <= stats.p10 THEN
        ROUND((p_value / NULLIF(stats.p10, 0)) * 10)
      WHEN p_value <= stats.p25 THEN
        10 + ROUND(((p_value - stats.p10) / NULLIF(stats.p25 - stats.p10, 0)) * 15)
      WHEN p_value <= stats.p50 THEN
        25 + ROUND(((p_value - stats.p25) / NULLIF(stats.p50 - stats.p25, 0)) * 25)
      WHEN p_value <= stats.p75 THEN
        50 + ROUND(((p_value - stats.p50) / NULLIF(stats.p75 - stats.p50, 0)) * 25)
      WHEN p_value <= stats.p90 THEN
        75 + ROUND(((p_value - stats.p75) / NULLIF(stats.p90 - stats.p75, 0)) * 15)
      ELSE
        90 + LEAST(9, ROUND(((p_value - stats.p90) / NULLIF(stats.p90 * 0.5, 0)) * 10))
    END
  ));
END;
$$ LANGUAGE plpgsql STABLE;