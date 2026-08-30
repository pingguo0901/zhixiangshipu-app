CREATE OR REPLACE FUNCTION public.get_daily_purchase_summary(p_date date)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE v json;
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION '权限不足';
  END IF;
  SELECT json_build_object(
    'dp_no', 'DP-' || to_char(p_date, 'YYYYMMDD') || '-001',
    'date', to_char(p_date, 'DD/MM/YYYY'),
    'items', COALESCE((
      SELECT jsonb_agg(x) FROM (
        SELECT jsonb_build_object(
          'name', COALESCE(w.item_name, 'Unknown'),
          'qty', ROUND(SUM((i->>'qty')::numeric), 2),
          'unit', COALESCE(MAX(NULLIF(w.unit, '')), 'KG'),
          'unit_price', ROUND(SUM((i->>'unit_price')::numeric) / NULLIF(SUM((i->>'qty')::numeric), 0), 2),
          'sub_total', ROUND(SUM((i->>'unit_price')::numeric), 2)
        ) AS x
        FROM stock_in_log s
        CROSS JOIN jsonb_array_elements(s.in_items) i
        LEFT JOIN warehouse_items w ON w.id = (i->>'warehouse_item_id')::bigint
        WHERE (s.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date = p_date
        GROUP BY w.item_name, w.unit
      ) t
    ), '[]'::jsonb),
    'payment', COALESCE((
      SELECT jsonb_object_agg(pm, total) FROM (
        SELECT pay_method AS pm, ROUND(SUM(total_cost_myr), 2) AS total
        FROM stock_in_log
        WHERE (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date = p_date
        GROUP BY pay_method
      ) pp
    ), '{}'::jsonb),
    'total', COALESCE((SELECT ROUND(SUM(total_cost_myr), 2) FROM stock_in_log WHERE (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date = p_date), 0)
  ) INTO v;
  RETURN v;
END
$function$;
