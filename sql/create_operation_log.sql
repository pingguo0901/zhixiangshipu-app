-- ============================================================
-- 炙巷食谱 操作日志表 operation_log（只记"谁在何时做了什么操作"）
-- 注意：不固化交易金额、找零等核心业务数据，核心数据在 receipt_master/receipt_item
-- ============================================================

CREATE TABLE IF NOT EXISTS public.operation_log (
    log_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- 日志唯一ID
    operator          TEXT NOT NULL DEFAULT '',                        -- 操作员/操作人
    operation_type    TEXT NOT NULL CHECK (operation_type IN ('print_receipt','refund','modify','login','logout')), -- 操作类型
    receipt_no        TEXT,                                            -- 关联收据编号（可选）
    operation_time    TIMESTAMPTZ NOT NULL DEFAULT now(),              -- 操作时间
    operation_content TEXT                                             -- 操作内容描述，如"打印了收据R-20260828-0001"
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_operation_log_time   ON public.operation_log(operation_time DESC);
CREATE INDEX IF NOT EXISTS idx_operation_log_receipt ON public.operation_log(receipt_no);

-- RLS
ALTER TABLE public.operation_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "operation_log_all" ON public.operation_log;
CREATE POLICY "operation_log_all" ON public.operation_log
  FOR ALL USING (true) WITH CHECK (true);
