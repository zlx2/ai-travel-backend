USE ai_travel;

-- 不创建固定密码的默认管理员。首次部署后应通过安全的初始化流程创建管理员账号。

INSERT INTO destination (name, province, city, description, tags_json, heat, status, deleted) VALUES
('重庆','重庆市','重庆市','山城、美食与夜景目的地',JSON_ARRAY('美食','夜景','山城'),100,1,0),
('成都','四川省','成都市','适合美食与轻松旅行',JSON_ARRAY('美食','轻松游','亲子游'),95,1,0),
('西安','陕西省','西安市','历史文化名城',JSON_ARRAY('历史文化','美食','拍照'),90,1,0),
('杭州','浙江省','杭州市','湖景与江南文化目的地',JSON_ARRAY('自然风光','情侣游','拍照'),88,1,0),
('厦门','福建省','厦门市','适合海滨与轻松旅行',JSON_ARRAY('海滨','情侣游','拍照'),86,1,0)
ON DUPLICATE KEY UPDATE status = 1, deleted = 0;

INSERT INTO tag (name, type, status, deleted) VALUES
('美食',1,1,0),('夜景',1,1,0),('自然风光',1,1,0),('亲子游',1,1,0),('情侣游',1,1,0),
('轻松游',2,1,0),('历史文化',2,1,0),('山城',3,1,0),('海滨',3,1,0),('拍照',1,1,0)
ON DUPLICATE KEY UPDATE status = 1, deleted = 0;
