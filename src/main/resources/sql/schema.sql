CREATE DATABASE IF NOT EXISTS ai_travel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ai_travel;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL, password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) NOT NULL, nickname VARCHAR(50) NOT NULL, avatar_url VARCHAR(500), role TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1, last_login_time DATETIME, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_username (username), UNIQUE KEY uk_email (email), KEY idx_status (status), KEY idx_create_time (create_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
  scene VARCHAR(32) NOT NULL, status TINYINT NOT NULL DEFAULT 1, context_json JSON,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_conversation_id (conversation_id), KEY idx_ai_conversation_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, conversation_id VARCHAR(64), scene VARCHAR(32) NOT NULL,
  model_name VARCHAR(64), request_json JSON, response_json JSON, success TINYINT NOT NULL DEFAULT 0,
  error_message VARCHAR(1000), duration_ms BIGINT, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_call_user_time (user_id, create_time), KEY idx_ai_call_conversation (conversation_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trip (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, conversation_id VARCHAR(64), title VARCHAR(128),
  departure VARCHAR(128) NOT NULL, destination VARCHAR(128) NOT NULL, days TINYINT NOT NULL, budget INT,
  preferences_json JSON, requirement_json JSON NOT NULL, trip_plan_json JSON NOT NULL, summary VARCHAR(1000), cover_url VARCHAR(512),
  source TINYINT NOT NULL DEFAULT 1, status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, KEY idx_trip_user_time (user_id, create_time), KEY idx_trip_destination (destination)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(128) NOT NULL, cover_url VARCHAR(512),
  destination VARCHAR(128), summary VARCHAR(1000), content LONGTEXT NOT NULL, status TINYINT NOT NULL DEFAULT 0,
  view_count INT NOT NULL DEFAULT 0, like_count INT NOT NULL DEFAULT 0, favorite_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_note_status_time (status, deleted, create_time), KEY idx_note_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, note_id BIGINT NOT NULL, tag_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_note_tag (note_id, tag_id), KEY idx_note_tag_tag (tag_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, note_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_note_like (note_id, user_id), KEY idx_note_like_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, note_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_note_favorite (note_id, user_id), KEY idx_note_favorite_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, note_id BIGINT NOT NULL, user_id BIGINT NOT NULL, content VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_comment_note_time (note_id, status, deleted, create_time), KEY idx_comment_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS destination (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, province VARCHAR(64), city VARCHAR(64),
  longitude DECIMAL(10,7), latitude DECIMAL(10,7), cover_url VARCHAR(512), description TEXT, tags_json JSON,
  heat INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_destination_name (name), KEY idx_destination_hot (status, deleted, heat)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL, type TINYINT NOT NULL, status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_tag_name_type (name, type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS file_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, biz_type VARCHAR(32) NOT NULL, file_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(512) NOT NULL, url VARCHAR(1000) NOT NULL, content_type VARCHAR(128), size BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_file_user (user_id), UNIQUE KEY uk_file_object_key (object_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, admin_id BIGINT NOT NULL, operation VARCHAR(128) NOT NULL,
  target_type VARCHAR(64), target_id BIGINT, content VARCHAR(1000), ip VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_admin_log_time (admin_id, create_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rental_vehicle_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, group_code VARCHAR(50) NOT NULL, group_name VARCHAR(100) NOT NULL,
  display_name VARCHAR(100), vehicle_class VARCHAR(50), body_type VARCHAR(50), energy_type VARCHAR(32) DEFAULT 'UNLIMITED',
  transmission VARCHAR(32), seats_min INT, seats_max INT, recommended_people VARCHAR(50),
  recommended_luggage VARCHAR(50), travel_tags VARCHAR(255), example_models VARCHAR(255), description TEXT,
  icon_url VARCHAR(512), status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_rental_group_code (group_code),
  KEY idx_rental_group_status_sort (status, deleted, sort_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rental_vehicle_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, group_id BIGINT, group_code VARCHAR(50), brand VARCHAR(64) NOT NULL,
  series VARCHAR(128) NOT NULL, series_full_name VARCHAR(255), model_year INT, launch_year INT,
  vehicle_class VARCHAR(32), body_type VARCHAR(32), energy_type VARCHAR(32), raw_energy_type VARCHAR(64),
  transmission VARCHAR(32), seats INT, guide_price_min_cent INT, guide_price_max_cent INT,
  image_url VARCHAR(512), source_url VARCHAR(1000), confidence VARCHAR(16) DEFAULT 'medium', note TEXT,
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_rental_model_group_id (group_id), KEY idx_rental_model_group_code (group_code),
  KEY idx_rental_model_brand_series (brand, series), KEY idx_rental_model_status (status, deleted)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rental_pickup_poi (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, source VARCHAR(32) NOT NULL DEFAULT 'AMAP',
  source_poi_id VARCHAR(64) NOT NULL, poi_name VARCHAR(255) NOT NULL, poi_type VARCHAR(255),
  poi_typecode VARCHAR(32), province VARCHAR(64), city VARCHAR(64), district VARCHAR(64),
  citycode VARCHAR(16), adcode VARCHAR(16), address VARCHAR(500), longitude DECIMAL(10,7) NOT NULL,
  latitude DECIMAL(10,7) NOT NULL, raw_json JSON, status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_rental_source_poi (source, source_poi_id),
  KEY idx_rental_poi_citycode (citycode), KEY idx_rental_poi_adcode (adcode),
  KEY idx_rental_poi_location (longitude, latitude), KEY idx_rental_poi_status (status, deleted)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rental_price_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, city VARCHAR(64) NOT NULL, citycode VARCHAR(16), adcode VARCHAR(16) NOT NULL DEFAULT '',
  vehicle_group_id BIGINT NOT NULL, weekday_rental_fee_cent INT NOT NULL DEFAULT 0,
  weekend_rental_fee_cent INT NOT NULL DEFAULT 0, base_service_fee_cent INT NOT NULL DEFAULT 0,
  vehicle_prepare_fee_cent INT NOT NULL DEFAULT 2000, rental_deposit_cent INT NOT NULL DEFAULT 0,
  violation_deposit_cent INT NOT NULL DEFAULT 200000, deposit_free_threshold_score INT DEFAULT 600,
  one_way_base_fee_cent INT NOT NULL DEFAULT 30000, one_way_per_km_fee_cent INT NOT NULL DEFAULT 0,
  one_way_discount_rate DECIMAL(4,2) DEFAULT 1.00, available_count INT DEFAULT 5,
  source_platform VARCHAR(64), source_note VARCHAR(1000), sampled_at DATETIME, confidence VARCHAR(16) DEFAULT 'medium',
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rental_city_adcode_group (city, adcode, vehicle_group_id),
  KEY idx_rental_price_citycode_group (citycode, vehicle_group_id), KEY idx_rental_price_group (vehicle_group_id),
  KEY idx_rental_price_status (status, deleted)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rental_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, trip_id BIGINT,
  pickup_poi_id BIGINT, pickup_mode VARCHAR(30) NOT NULL DEFAULT 'POI', return_poi_id BIGINT,
  return_mode VARCHAR(30) NOT NULL DEFAULT 'POI', delivery_address VARCHAR(500), return_address VARCHAR(500),
  delivery_fee_cent INT NOT NULL DEFAULT 0, pickup_poi_snapshot JSON NOT NULL, return_poi_snapshot JSON NOT NULL,
  vehicle_group_id BIGINT NOT NULL, assigned_model_id BIGINT, pickup_time DATETIME NOT NULL, return_time DATETIME NOT NULL,
  rental_days DECIMAL(5,2) NOT NULL, is_one_way TINYINT NOT NULL DEFAULT 0, rental_fee_cent INT NOT NULL DEFAULT 0,
  base_service_fee_cent INT NOT NULL DEFAULT 0, vehicle_prepare_fee_cent INT NOT NULL DEFAULT 2000,
  one_way_base_fee_cent INT NOT NULL DEFAULT 0, one_way_discount_cent INT NOT NULL DEFAULT 0,
  one_way_final_fee_cent INT NOT NULL DEFAULT 0, rental_deposit_cent INT NOT NULL DEFAULT 0,
  violation_deposit_cent INT NOT NULL DEFAULT 0, deposit_free_threshold_score INT DEFAULT 600,
  total_price_cent INT NOT NULL, price_template_id BIGINT, price_snapshot JSON, contact_name VARCHAR(50),
  contact_phone VARCHAR(50), order_status VARCHAR(30) NOT NULL DEFAULT 'pending',
  payment_status VARCHAR(30) NOT NULL DEFAULT 'unpaid', remark VARCHAR(1000),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_rental_order_no (order_no), KEY idx_rental_order_user_time (user_id, create_time),
  KEY idx_rental_order_trip (trip_id), KEY idx_rental_order_status (order_status, payment_status),
  KEY idx_rental_order_pickup_time (pickup_time), KEY idx_rental_order_vehicle_group (vehicle_group_id)
) ENGINE=InnoDB;
