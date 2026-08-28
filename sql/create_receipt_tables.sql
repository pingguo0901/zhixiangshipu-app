-- ============================================================
-- 炙巷食谱 收据系统：销售主表 + 销售明细表
-- 合规：LHDN 收据审计（receipt_no 连续递增、不可跳号、不可重复）
-- ============================================================

-- 表1：销售主表 receipt_master（一笔收据一行）
CREATE TABLE IF NOT EXISTS public.receipt_master (
    receipt_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- 系统内部唯一ID
    receipt_no      TEXT NOT NULL UNIQUE,                             -- 收据编号 R-YYYYMMDD-0001（触发器生成）
    ssm_brand       TEXT NOT NULL DEFAULT 'ZHI XIANG FOOD ENTERPRISE', -- SSM注册名（固定值）
    ssm_brn         TEXT,                                             -- 12位BRN号码（注册完成后填入）
    trans_datetime  TIMESTAMPTZ NOT NULL DEFAULT now(),               -- 交易日期时间（精确到分钟）
    sub_total       NUMERIC(12,2) NOT NULL DEFAULT 0,                 -- 商品小计
    discount        NUMERIC(12,2) NOT NULL DEFAULT 0,                 -- 折扣金额
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,                 -- 最终总金额（小计-折扣）
    payment_mode    TEXT NOT NULL CHECK (payment_mode IN ('CASH','DUITNOW','TNG','GRABPAY')), -- 付款方式
    amount_received NUMERIC(12,2) NOT NULL DEFAULT 0,                 -- 实收金额
    change_given    NUMERIC(12,2) NOT NULL DEFAULT 0,                 -- 找零金额（实收-总金额）
    operator        TEXT NOT NULL DEFAULT '',                         -- 操作员/收银员
    create_time     TIMESTAMPTZ NOT NULL DEFAULT now(),               -- 记录创建时间
    remark          TEXT                                              -- 备注（桌号/外卖单号等）
);

-- 表2：销售明细表 receipt_item（一笔收据多行，一行一个商品）
CREATE TABLE IF NOT EXISTS public.receipt_item (
    item_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,      -- 明细唯一ID
    receipt_no  TEXT NOT NULL REFERENCES public.receipt_master(receipt_no) ON DELETE CASCADE, -- 关联主表
    item_name   TEXT NOT NULL,                                       -- 商品名称
    qty         INTEGER NOT NULL DEFAULT 1,                           -- 购买数量
    unit_price  NUMERIC(12,2) NOT NULL DEFAULT 0,                     -- 单价
    item_amount NUMERIC(12,2) NOT NULL DEFAULT 0                      -- 该行小计（数量×单价）
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_receipt_master_trans ON public.receipt_master(trans_datetime DESC);
CREATE INDEX IF NOT EXISTS idx_receipt_master_no    ON public.receipt_master(receipt_no);
CREATE INDEX IF NOT EXISTS idx_receipt_item_no      ON public.receipt_item(receipt_no);

-- ============================================================
-- 收据编号自动生成触发器：R-YYYYMMDD-连续序号（每天重置，加事务锁防并发跳号）
-- 注意：收据记录不可删除（删除会导致序号跳号，违反 LHDN 合规）
-- ============================================================
CREATE OR REPLACE FUNCTION public.gen_receipt_master_no()
RETURNS TRIGGER AS $$
DECLARE
    d TEXT;
    n INT;
BEGIN
    d := to_char(NEW.trans_datetime AT TIME ZONE 'Asia/Kuala_Lumpur', 'YYYYMMDD');
    PERFORM pg_advisory_xact_lock(hashtext('receipt_master_no'));
    SELECT COALESCE(MAX(SUBSTRING(receipt_no FROM '[0-9]+$')::int), 0) + 1
      INTO n
      FROM public.receipt_master
     WHERE receipt_no LIKE 'R-' || d || '-%';
    NEW.receipt_no := 'R-' || d || '-' || LPAD(n::text, 4, '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_receipt_master_no ON public.receipt_master;
CREATE TRIGGER trg_receipt_master_no
  BEFORE INSERT ON public.receipt_master
  FOR EACH ROW
  WHEN (NEW.receipt_no IS NULL OR NEW.receipt_no = '')
  EXECUTE FUNCTION public.gen_receipt_master_no();

-- ============================================================
-- RLS：允许已登录员工读写（内部收银系统，后续可收紧）
-- ============================================================
ALTER TABLE public.receipt_master ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipt_item   ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "receipt_master_all" ON public.receipt_master;
CREATE POLICY "receipt_master_all" ON public.receipt_master
  FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "receipt_item_all" ON public.receipt_item;
CREATE POLICY "receipt_item_all" ON public.receipt_item
  FOR ALL USING (true) WITH CHECK (true);
