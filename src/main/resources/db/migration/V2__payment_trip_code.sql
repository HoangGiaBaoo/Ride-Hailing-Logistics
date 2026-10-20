-- Ma chuyen hien thi tren bien nhan va mo ta but toan vi; snapshot luc tao thanh toan.
ALTER TABLE payments ADD COLUMN trip_code varchar(20);
UPDATE payments SET trip_code = '' WHERE trip_code IS NULL;
ALTER TABLE payments ALTER COLUMN trip_code SET NOT NULL;
