-- 协议级订阅 Topic 过滤：| 分隔，NULL/空 = 接收数据源的全部 topic
ALTER TABLE protocol ADD COLUMN source_topics VARCHAR(1024);
