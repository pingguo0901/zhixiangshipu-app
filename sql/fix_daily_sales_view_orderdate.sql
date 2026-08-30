-- 报表列表视图：营业额改为按下单时间 order_datetime 归账（与订单数口径一致）
CREATE OR REPLACE VIEW public.daily_sales_view AS
WITH sales AS (
  SELECT (c.order_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date AS period_date,
         sum(p.pay_amount_myr) AS total_sales_myr
  FROM payment_records p
  JOIN customer_orders c ON c.id = p.order_id
  GROUP BY ((c.order_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date)
), stock AS (
  SELECT (stock_in_log.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date AS period_date,
         sum(stock_in_log.total_cost_myr) AS total_stock_cost_myr
  FROM stock_in_log
  GROUP BY ((stock_in_log.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date)
), expense AS (
  SELECT (expense_records.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date AS period_date,
         sum(expense_records.amount_myr) AS total_expense_myr
  FROM expense_records
  WHERE expense_records.is_personal = false
  GROUP BY ((expense_records.transaction_datetime AT TIME ZONE 'Asia/Kuala_Lumpur')::date)
)
SELECT COALESCE(s.period_date, st.period_date, e.period_date) AS period_date,
       COALESCE(s.total_sales_myr, 0::numeric) AS total_sales_myr,
       COALESCE(st.total_stock_cost_myr, 0::numeric) AS total_stock_cost_myr,
       COALESCE(e.total_expense_myr, 0::numeric) AS total_expense_myr,
       (COALESCE(s.total_sales_myr, 0::numeric) - COALESCE(st.total_stock_cost_myr, 0::numeric)) - COALESCE(e.total_expense_myr, 0::numeric) AS gross_profit_myr
FROM ((sales s FULL JOIN stock st USING (period_date)) FULL JOIN expense e USING (period_date))
ORDER BY COALESCE(s.period_date, st.period_date, e.period_date);
