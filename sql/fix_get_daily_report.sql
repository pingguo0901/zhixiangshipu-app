CREATE OR REPLACE FUNCTION public.get_daily_report(p_start date, p_end date)
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
    'total_orders', (SELECT count(*) FROM customer_orders WHERE (order_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),
    'paid_orders', (SELECT count(*) FROM customer_orders WHERE payment_status='paid' AND (order_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),
    'total_sales', COALESCE((SELECT sum(pay_amount_myr) FROM payment_records WHERE (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'total_discount', COALESCE((SELECT sum(discount) FROM receipt_master WHERE (trans_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'cash', COALESCE((SELECT sum(pay_amount_myr) FROM payment_records WHERE pay_method='cash' AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'duitnow', COALESCE((SELECT sum(pay_amount_myr) FROM payment_records WHERE pay_method='duitnow' AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'tng', COALESCE((SELECT sum(pay_amount_myr) FROM payment_records WHERE pay_method='tng_ewallet' AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'alipay', COALESCE((SELECT sum(pay_amount_myr) FROM payment_records WHERE pay_method='alipay' AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'total_stock_cost', COALESCE((SELECT sum(total_cost_myr) FROM stock_in_log WHERE (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'stock_breakdown', COALESCE((
      SELECT jsonb_agg(t.x) FROM (
        SELECT jsonb_build_object(
          'name', COALESCE(w.item_name, '未知物品'),
          'qty', ROUND(SUM((i->>'qty')::numeric), 2),
          'unit', COALESCE(NULLIF(w.unit, ''), 'KG')
        ) AS x
        FROM stock_in_log s
        CROSS JOIN jsonb_array_elements(s.in_items) AS i
        LEFT JOIN warehouse_items w ON w.id = (i->>'warehouse_item_id')::bigint
        WHERE (s.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end
        GROUP BY w.item_name, w.unit
      ) t
    ), '[]'::jsonb),
    'total_expense', COALESCE((SELECT sum(amount_myr) FROM expense_records WHERE is_personal=false AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end),0),
    'skewers', COALESCE((SELECT jsonb_agg(t.x) FROM (SELECT jsonb_build_object('name', i->>'item_name', 'name_en', COALESCE(NULLIF(i->>'name_en',''), i->>'item_name'), 'qty', SUM((i->>'quantity')::int)) AS x FROM customer_orders c, jsonb_array_elements(c.order_items) AS i WHERE (c.order_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end GROUP BY i->>'item_name', i->>'name_en') t), '[]'::jsonb),
    'expense_breakdown', COALESCE((
      SELECT jsonb_object_agg(expense_type, total)
      FROM (
        SELECT expense_type, sum(amount_myr) AS total
        FROM expense_records
        WHERE is_personal=false AND (transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date BETWEEN p_start AND p_end
        GROUP BY expense_type
      ) t
    ),'{}'::jsonb)
  ) INTO v;
  RETURN v;
END
$function$;
