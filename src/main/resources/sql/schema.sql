-- ===================================================================
-- ai_travel 数据库完整建表 SQL
-- 用于 AI 理解项目表结构和关系
-- 以 dev 分支实际数据库为准
-- ===================================================================

CREATE DATABASE IF NOT EXISTS ai_travel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ai_travel;

-- ===================================================================
-- 1. 用户 & 认证
-- ===================================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  avatar_url VARCHAR(500),
  role TINYINT NOT NULL DEFAULT 1 COMMENT '1=普通用户',
  status TINYINT NOT NULL DEFAULT 1,
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_email (email),
  KEY idx_status (status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='系统用户表，仅支持普通用户，无管理员功能';

-- ===================================================================
-- 2. 行程（已完成的行程）
-- ===================================================================

CREATE TABLE IF NOT EXISTS trip (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  conversation_id VARCHAR(64),
  title VARCHAR(100) NOT NULL,
  departure VARCHAR(100),
  destination VARCHAR(100) NOT NULL,
  days TINYINT NOT NULL,
  budget INT,
  preferences_json JSON,
  requirement_json JSON NOT NULL,
  trip_plan_json JSON NOT NULL,
  summary VARCHAR(500),
  cover_url VARCHAR(500),
  source TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_trip_user_time (user_id, create_time),
  KEY idx_trip_destination (destination)
) ENGINE=InnoDB COMMENT='行程表，AI 生成完成的行程';

-- ===================================================================
-- 3. 游记（社区功能）
-- ===================================================================

CREATE TABLE IF NOT EXISTS note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  cover_url VARCHAR(512),
  destination VARCHAR(128),
  summary VARCHAR(1000),
  content LONGTEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=发布',
  view_count INT NOT NULL DEFAULT 0,
  like_count INT NOT NULL DEFAULT 0,
  favorite_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_note_status_time (status, deleted, create_time),
  KEY idx_note_user (user_id)
) ENGINE=InnoDB COMMENT='游记表';

CREATE TABLE IF NOT EXISTS note_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_note_tag (note_id, tag_id),
  KEY idx_note_tag_tag (tag_id)
) ENGINE=InnoDB COMMENT='游记-标签关联表';

CREATE TABLE IF NOT EXISTS note_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_note_like (note_id, user_id),
  KEY idx_note_like_user (user_id)
) ENGINE=InnoDB COMMENT='游记点赞表';

CREATE TABLE IF NOT EXISTS note_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_note_favorite (note_id, user_id),
  KEY idx_note_favorite_user (user_id)
) ENGINE=InnoDB COMMENT='游记收藏表';

CREATE TABLE IF NOT EXISTS note_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_comment_note_time (note_id, status, deleted, create_time),
  KEY idx_comment_user (user_id)
) ENGINE=InnoDB COMMENT='游记评论表';

-- ===================================================================
-- 4. 标签 & 目的地
-- ===================================================================

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  type TINYINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tag_name_type (name, type)
) ENGINE=InnoDB COMMENT='标签表';

CREATE TABLE IF NOT EXISTS destination (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  province VARCHAR(64),
  city VARCHAR(64),
  longitude DECIMAL(10,7),
  latitude DECIMAL(10,7),
  cover_url VARCHAR(512),
  description TEXT,
  tags_json JSON,
  heat INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_destination_name (name),
  KEY idx_destination_hot (status, deleted, heat)
) ENGINE=InnoDB COMMENT='热门目的地表';

-- ===================================================================
-- 5. 文件资源
-- ===================================================================

CREATE TABLE IF NOT EXISTS file_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  url VARCHAR(1000) NOT NULL,
  content_type VARCHAR(128),
  size BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_file_user (user_id),
  UNIQUE KEY uk_file_object_key (object_key)
) ENGINE=InnoDB COMMENT='文件资源表（OSS 文件索引）';

-- ===================================================================
-- 6. 旅游知识库（AI 路线规划核心数据，通过离线脚本构建）
-- ===================================================================

CREATE TABLE IF NOT EXISTS travel_city (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  city_name VARCHAR(64) NOT NULL COMMENT '城市名称',
  province VARCHAR(64) DEFAULT NULL COMMENT '所属省份',
  amap_citycode VARCHAR(16) DEFAULT NULL COMMENT '高德城市编码',
  amap_adcode VARCHAR(16) DEFAULT NULL COMMENT '高德行政区编码',
  center_lng DECIMAL(10,7) DEFAULT NULL COMMENT '城市中心经度',
  center_lat DECIMAL(10,7) DEFAULT NULL COMMENT '城市中心纬度',
  PRIMARY KEY (id),
  UNIQUE KEY uk_city_name (city_name)
) ENGINE=InnoDB COMMENT='旅游城市表，路线生成入口';

CREATE TABLE IF NOT EXISTS travel_area (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  city_id BIGINT NOT NULL COMMENT '所属城市',
  area_name VARCHAR(128) NOT NULL COMMENT '片区名称',
  center_lng DECIMAL(10,7) NOT NULL COMMENT '片区中心经度',
  center_lat DECIMAL(10,7) NOT NULL COMMENT '片区中心纬度',
  radius_km DECIMAL(6,2) DEFAULT NULL COMMENT '片区半径（公里）',
  area_type VARCHAR(32) NOT NULL COMMENT '片区类型：CORE/HISTORIC/BUSINESS/NATURE/THEME_PARK/ANCIENT_TOWN/MUSEUM/VIEWPOINT/SUBURB',
  ring_level VARCHAR(16) DEFAULT NULL COMMENT '圈层：CORE/INNER/OUTER/SUBURB/FAR',
  direction VARCHAR(8) DEFAULT NULL COMMENT '相对城市中心方位',
  tags_json JSON DEFAULT NULL COMMENT '标签数组',
  priority_score INT NOT NULL DEFAULT 0 COMMENT '片区优先级 0-100',
  suggested_duration_hours DECIMAL(4,1) DEFAULT NULL COMMENT '建议游玩时长（小时）',
  exclusive_day TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议独占一天',
  PRIMARY KEY (id),
  KEY idx_city_id (city_id),
  KEY idx_area_type (area_type),
  KEY idx_priority (priority_score)
) ENGINE=InnoDB COMMENT='旅游片区表，路线骨架核心（禁止手动修改）';

CREATE TABLE IF NOT EXISTS travel_spot (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，AI 输出时必须引用此 ID',
  city_id BIGINT NOT NULL COMMENT '所属城市',
  area_id BIGINT NOT NULL COMMENT '所属片区',
  source VARCHAR(16) NOT NULL DEFAULT 'AMAP' COMMENT '数据来源：AMAP/MANUAL/AI/IMPORT',
  source_poi_id VARCHAR(128) DEFAULT NULL COMMENT '高德 POI ID',
  spot_name VARCHAR(255) NOT NULL COMMENT '景点名称',
  alias_json JSON DEFAULT NULL COMMENT '别名数组',
  lng DECIMAL(10,7) NOT NULL COMMENT '经度',
  lat DECIMAL(10,7) NOT NULL COMMENT '纬度',
  address VARCHAR(500) DEFAULT NULL COMMENT '地址',
  spot_type VARCHAR(32) NOT NULL COMMENT '景点类型：LANDMARK/MUSEUM/PARK/TEMPLE/OLD_STREET/NATURE/THEME_PARK/VIEWPOINT/HISTORIC/CULTURE/CAMPUS/WATERFRONT',
  tags_json JSON DEFAULT NULL COMMENT '标签数组',
  value_level CHAR(1) NOT NULL DEFAULT 'B' COMMENT '价值等级：S/A/B/C',
  quality_score INT NOT NULL DEFAULT 0 COMMENT '质量分 0-100',
  popularity_score INT NOT NULL DEFAULT 0 COMMENT '热度分 0-100',
  recommended_duration_min INT DEFAULT NULL COMMENT '推荐游玩时长（分钟）',
  best_time_json JSON DEFAULT NULL COMMENT '最佳游玩时间',
  open_time_text VARCHAR(255) DEFAULT NULL COMMENT '开放时间文本',
  ticket_text VARCHAR(255) DEFAULT NULL COMMENT '门票文本',
  physical_level VARCHAR(8) DEFAULT NULL COMMENT '体力强度：LOW/MEDIUM/HIGH',
  rain_friendly TINYINT NOT NULL DEFAULT 0 COMMENT '是否适合雨天',
  night_friendly TINYINT NOT NULL DEFAULT 0 COMMENT '是否适合夜晚',
  family_friendly TINYINT NOT NULL DEFAULT 0 COMMENT '是否适合亲子',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_poi (source, source_poi_id),
  KEY idx_city_id (city_id),
  KEY idx_area_id (area_id),
  KEY idx_spot_type (spot_type),
  KEY idx_value_level (value_level)
) ENGINE=InnoDB COMMENT='高价值景点表，AI 只能从此表选点（禁止手动修改）';

-- ===================================================================
-- 7. 租车业务
-- ===================================================================

CREATE TABLE IF NOT EXISTS rental_vehicle_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_code VARCHAR(50) NOT NULL COMMENT '车型组编码',
  group_name VARCHAR(100) NOT NULL COMMENT '车型组名称',
  display_name VARCHAR(100) DEFAULT NULL COMMENT '展示名称',
  vehicle_class VARCHAR(50) DEFAULT NULL,
  body_type VARCHAR(50) DEFAULT NULL,
  energy_type VARCHAR(32) DEFAULT 'UNLIMITED' COMMENT 'FUEL/ELECTRIC/HYBRID/UNLIMITED',
  transmission VARCHAR(32) DEFAULT '自动挡',
  seats_min INT DEFAULT NULL,
  seats_max INT DEFAULT NULL,
  recommended_people VARCHAR(50) DEFAULT NULL,
  recommended_luggage VARCHAR(50) DEFAULT NULL,
  travel_tags VARCHAR(255) DEFAULT NULL,
  example_models VARCHAR(255) DEFAULT NULL,
  description TEXT,
  icon_url VARCHAR(512) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_code (group_code),
  KEY idx_status_sort (status, deleted, sort_order)
) ENGINE=InnoDB COMMENT='租车车型组表';

CREATE TABLE IF NOT EXISTS rental_vehicle_model (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_id BIGINT DEFAULT NULL,
  group_code VARCHAR(50) DEFAULT NULL,
  brand VARCHAR(64) NOT NULL,
  series VARCHAR(128) NOT NULL,
  series_full_name VARCHAR(255) DEFAULT NULL,
  model_year INT DEFAULT NULL,
  launch_year INT DEFAULT NULL,
  vehicle_class VARCHAR(32) DEFAULT NULL,
  body_type VARCHAR(32) DEFAULT NULL,
  energy_type VARCHAR(32) DEFAULT NULL,
  raw_energy_type VARCHAR(64) DEFAULT NULL,
  transmission VARCHAR(32) DEFAULT '自动挡',
  seats INT DEFAULT NULL,
  guide_price_min_cent INT DEFAULT NULL,
  guide_price_max_cent INT DEFAULT NULL,
  image_url VARCHAR(512) DEFAULT NULL,
  source_url VARCHAR(1000) DEFAULT NULL,
  confidence VARCHAR(16) DEFAULT 'medium',
  note TEXT,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  summary VARCHAR(255) DEFAULT NULL,
  feature_tags VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_group_id (group_id),
  KEY idx_group_code (group_code),
  KEY idx_brand_series (brand, series),
  KEY idx_status (status, deleted)
) ENGINE=InnoDB COMMENT='租车车型基础表';

CREATE TABLE IF NOT EXISTS rental_price_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  city VARCHAR(64) NOT NULL,
  citycode VARCHAR(16) DEFAULT NULL,
  adcode VARCHAR(16) NOT NULL DEFAULT '',
  vehicle_group_id BIGINT NOT NULL,
  weekday_rental_fee_cent INT NOT NULL DEFAULT 0,
  weekend_rental_fee_cent INT NOT NULL DEFAULT 0,
  base_service_fee_cent INT NOT NULL DEFAULT 0,
  vehicle_prepare_fee_cent INT NOT NULL DEFAULT 2000,
  rental_deposit_cent INT NOT NULL DEFAULT 0,
  violation_deposit_cent INT NOT NULL DEFAULT 200000,
  deposit_free_threshold_score INT DEFAULT 600,
  one_way_base_fee_cent INT NOT NULL DEFAULT 30000,
  one_way_per_km_fee_cent INT NOT NULL DEFAULT 0,
  one_way_discount_rate DECIMAL(4,2) DEFAULT 1.00,
  available_count INT DEFAULT 5,
  source_platform VARCHAR(64) DEFAULT NULL,
  source_note VARCHAR(1000) DEFAULT NULL,
  sampled_at DATETIME DEFAULT NULL,
  confidence VARCHAR(16) DEFAULT 'medium',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  daily_mileage_limit_km INT DEFAULT 200,
  extra_mileage_fee_cent INT DEFAULT 200,
  included_services VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_city_adcode_group (city, adcode, vehicle_group_id),
  KEY idx_citycode_group (citycode, vehicle_group_id),
  KEY idx_group (vehicle_group_id),
  KEY idx_status (status, deleted)
) ENGINE=InnoDB COMMENT='租车价格模板表';

CREATE TABLE IF NOT EXISTS rental_pickup_poi (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source VARCHAR(32) NOT NULL DEFAULT 'AMAP',
  source_poi_id VARCHAR(64) NOT NULL,
  poi_name VARCHAR(255) NOT NULL,
  poi_type VARCHAR(255) DEFAULT NULL,
  poi_typecode VARCHAR(32) DEFAULT NULL,
  province VARCHAR(64) DEFAULT NULL,
  city VARCHAR(64) DEFAULT NULL,
  district VARCHAR(64) DEFAULT NULL,
  citycode VARCHAR(16) DEFAULT NULL,
  adcode VARCHAR(16) DEFAULT NULL,
  address VARCHAR(500) DEFAULT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  raw_json JSON DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_poi (source, source_poi_id),
  KEY idx_citycode (citycode),
  KEY idx_adcode (adcode),
  KEY idx_location (longitude, latitude),
  KEY idx_status (status, deleted)
) ENGINE=InnoDB COMMENT='租车取还车 POI 地点表';

CREATE TABLE IF NOT EXISTS rental_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  trip_id BIGINT DEFAULT NULL,
  pickup_poi_id BIGINT DEFAULT NULL,
  pickup_mode VARCHAR(30) NOT NULL DEFAULT 'POI',
  return_poi_id BIGINT DEFAULT NULL,
  return_mode VARCHAR(30) NOT NULL DEFAULT 'POI',
  delivery_address VARCHAR(500) DEFAULT NULL,
  return_address VARCHAR(500) DEFAULT NULL,
  delivery_fee_cent INT NOT NULL DEFAULT 0,
  pickup_poi_snapshot JSON NOT NULL,
  return_poi_snapshot JSON NOT NULL,
  vehicle_group_id BIGINT NOT NULL,
  assigned_model_id BIGINT DEFAULT NULL,
  pickup_time DATETIME NOT NULL,
  return_time DATETIME NOT NULL,
  rental_days DECIMAL(5,2) NOT NULL,
  is_one_way TINYINT NOT NULL DEFAULT 0,
  rental_fee_cent INT NOT NULL DEFAULT 0,
  base_service_fee_cent INT NOT NULL DEFAULT 0,
  vehicle_prepare_fee_cent INT NOT NULL DEFAULT 2000,
  one_way_base_fee_cent INT NOT NULL DEFAULT 0,
  one_way_discount_cent INT NOT NULL DEFAULT 0,
  one_way_final_fee_cent INT NOT NULL DEFAULT 0,
  rental_deposit_cent INT NOT NULL DEFAULT 0,
  violation_deposit_cent INT NOT NULL DEFAULT 0,
  deposit_free_threshold_score INT DEFAULT 600,
  total_price_cent INT NOT NULL,
  price_template_id BIGINT DEFAULT NULL,
  price_snapshot JSON DEFAULT NULL,
  contact_name VARCHAR(50) DEFAULT NULL,
  contact_phone VARCHAR(50) DEFAULT NULL,
  order_status VARCHAR(30) NOT NULL DEFAULT 'pending',
  payment_status VARCHAR(30) NOT NULL DEFAULT 'unpaid',
  remark VARCHAR(1000) DEFAULT NULL,
  protection_package_code VARCHAR(50) DEFAULT NULL,
  protection_package_name VARCHAR(100) DEFAULT NULL,
  protection_fee_cent INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_time (user_id, create_time),
  KEY idx_trip (trip_id),
  KEY idx_status (order_status, payment_status),
  KEY idx_pickup_time (pickup_time),
  KEY idx_vehicle_group (vehicle_group_id)
) ENGINE=InnoDB COMMENT='租车订单表';

-- ===================================================================
-- 8. AI 行程生成（多轮生成工作流）
-- ===================================================================

CREATE TABLE IF NOT EXISTS ai_trip_generation_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  conversation_id VARCHAR(64) DEFAULT NULL,
  requirement_json JSON NOT NULL,
  selected_quote_json JSON DEFAULT NULL,
  rental_trip_context_json JSON DEFAULT NULL,
  day_skeletons_json JSON NOT NULL,
  city_profile_json JSON DEFAULT NULL,
  weather_json JSON DEFAULT NULL,
  hotel_json JSON DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PREPARING',
  error_message VARCHAR(1000) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_session_user_time (user_id, create_time)
) ENGINE=InnoDB COMMENT='AI 行程生成会话表';

CREATE TABLE IF NOT EXISTS ai_trip_day_generation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  day_no INT NOT NULL,
  generation_version INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'GENERATING',
  is_current TINYINT NOT NULL DEFAULT 1,
  request_mode VARCHAR(32) NOT NULL DEFAULT 'USER',
  result_json JSON DEFAULT NULL,
  error_message VARCHAR(1000) DEFAULT NULL,
  started_at DATETIME DEFAULT NULL,
  finished_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_day_version (session_id, day_no, generation_version),
  KEY idx_day_status (status, update_time),
  KEY idx_day_user_session (user_id, session_id),
  KEY idx_day_current (session_id, day_no, is_current)
) ENGINE=InnoDB COMMENT='AI 逐日行程生成表';
