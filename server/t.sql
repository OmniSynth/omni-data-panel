WITH t1 AS (
  SELECT 
    CASE :ff   
      WHEN '时'   THEN DATE_FORMAT(d.create_time, '%Y/%m/%d %H时')
      WHEN '天'   THEN DATE_FORMAT(d.create_time, '%Y/%m/%d')
      WHEN '月'   THEN DATE_FORMAT(d.create_time, '%Y/%m')
      WHEN '合计' THEN '合计'
      ELSE NULL 
    END AS `time_dim`,
		c.channel_name,
    c.id,
    COUNT(DISTINCT o.supplier_order_id) AS `order_cnt`,
    SUM(d.pay_amount) AS sum_pay
  FROM ysh_app.app_add_service_order o  
  LEFT JOIN ysh_app.app_add_service_third_pay_serial d 
    ON d.add_service_order_id = o.id AND d.pay_status = 2
  LEFT JOIN ysh_system.sys_channel c 
    ON c.id = o.channel_id
  WHERE d.create_time IS NOT NULL 
    AND o.type = 3 
    -- omni: 原 Metabase 可选块，已始终保留
AND DATE(d.create_time) BETWEEN :start_date AND :end_date
  GROUP BY 1,2,3
),


t2 AS (
  select 
    time_dim,
    channel_id,
    add_service_order_id,
    sum(refund_amount) as refund_amount
  from (
    select 
      case :ff   
        when '时' then date_format(s.create_time,'%Y/%m/%d %H时')
        when '天'  then date_format(s.create_time,'%Y/%m/%d')  
        when '月'  then date_format(s.create_time,'%Y/%m')  
        when '合计' then '合计'   
        else null 
      end as time_dim,
      o.channel_id,
      s.refund_amount,
      s.add_service_order_id
    from app_add_service_refund_serial s 
    left join app_add_service_order o 
      on s.add_service_order_id = o.supplier_order_id
    where s.refund_status = 2
      -- omni: 原 Metabase 可选块，已始终保留
and date(s.create_time) BETWEEN :start_date and :end_date
  ) tmp
  group by time_dim, channel_id, add_service_order_id
),


t3 AS (
  SELECT 
    t2.time_dim,
		t2.channel_id,
    SUM(t2.refund_amount) AS sum_refund,
    COUNT(t2.add_service_order_id) AS re_order   
  FROM t2 
  GROUP BY 1,2
),

t4 as (
  SELECT
	t2.time_dim,
	t2.channel_id,
	t2. add_service_order_id,
	SUM(CASE WHEN p.pay_status = 2 THEN p.pay_amount ELSE 0 END) AS pay_amount
	FROM t2 
  LEFT JOIN app_add_service_third_pay_serial p 
    ON p.add_service_order_id = t2.add_service_order_id AND p.pay_status = 2
	group by 1,2)
SELECT * FROM t1