-- =====================================================================
-- 부품 카탈로그 시드 (확장판, 40종)
-- category_id: 1=CPU 2=GPU 3=MB 4=RAM 5=PSU 6=COOLER 7=STORAGE 8=CASE
-- 가격(part_prices)은 네이버 쇼핑 API 배치로 채운다.
-- =====================================================================
SET NAMES utf8mb4;

-- 재실행 대비 초기화
DELETE FROM cooler_socket_support;
DELETE FROM case_formfactor_support;
DELETE FROM cpu_specs; DELETE FROM gpu_specs; DELETE FROM mainboard_specs;
DELETE FROM ram_specs; DELETE FROM psu_specs; DELETE FROM cooler_specs;
DELETE FROM storage_specs; DELETE FROM case_specs;
DELETE FROM part_prices;
DELETE FROM parts;

-- ---------------- 부품 마스터 ----------------
INSERT INTO parts (part_id, category_id, manufacturer, model_name, release_date) VALUES
 (1, 1,'AMD','AMD 라이젠5 7600','2023-01-10'),
 (2, 1,'AMD','AMD 라이젠7 7700','2023-01-10'),
 (3, 1,'AMD','AMD 라이젠7 7800X3D','2023-04-06'),
 (4, 1,'AMD','AMD 라이젠9 7900X','2022-09-27'),
 (5, 1,'AMD','AMD 라이젠9 7950X','2022-09-27'),
 (6, 1,'Intel','인텔 코어 i5-14600K','2023-10-17'),
 (7, 1,'Intel','인텔 코어 i7-14700K','2023-10-17'),
 (8, 1,'Intel','인텔 코어 i9-14900K','2023-10-17'),
 (9, 2,'NVIDIA','GeForce RTX 4060','2023-06-29'),
 (10,2,'NVIDIA','GeForce RTX 4060 Ti 16GB','2023-07-18'),
 (11,2,'NVIDIA','GeForce RTX 4070 SUPER','2024-01-17'),
 (12,2,'NVIDIA','GeForce RTX 4070 Ti SUPER','2024-01-24'),
 (13,2,'NVIDIA','GeForce RTX 4080 SUPER','2024-01-31'),
 (14,2,'AMD','Radeon RX 7800 XT','2023-09-06'),
 (15,3,'ASUS','ASUS TUF B650-PLUS','2022-09-27'),
 (16,3,'MSI','MSI B650M MORTAR','2022-10-01'),
 (17,3,'ASUS','ASUS ROG STRIX X670E-A','2022-09-27'),
 (18,3,'MSI','MSI PRO B760M-A','2023-01-05'),
 (19,3,'ASUS','ASUS TUF B760-PLUS','2023-01-05'),
 (20,3,'MSI','MSI MAG Z790 TOMAHAWK','2022-10-20'),
 (21,4,'삼성','삼성 DDR5-5600 16GB(8x2)','2023-03-01'),
 (22,4,'삼성','삼성 DDR5-6000 32GB(16x2)','2023-03-01'),
 (23,4,'G.SKILL','G.SKILL DDR5-6400 32GB(16x2)','2023-05-01'),
 (24,4,'G.SKILL','G.SKILL DDR5-6000 64GB(32x2)','2023-05-01'),
 (25,5,'시소닉','시소닉 FOCUS GX-650 650W Gold','2022-06-01'),
 (26,5,'시소닉','시소닉 FOCUS GX-750 750W Gold','2022-06-01'),
 (27,5,'시소닉','시소닉 FOCUS GX-850 850W Gold','2022-06-01'),
 (28,5,'시소닉','시소닉 PRIME TX-1000 1000W Platinum','2022-06-01'),
 (29,6,'써멀라이트','써멀라이트 Peerless Assassin 120','2021-11-01'),
 (30,6,'딥쿨','딥쿨 AK620','2022-03-01'),
 (31,6,'ARCTIC','ARCTIC Liquid Freezer II 240','2022-02-01'),
 (32,6,'ARCTIC','ARCTIC Liquid Freezer II 360','2022-02-01'),
 (33,7,'삼성','삼성 990 PRO 1TB NVMe','2022-10-01'),
 (34,7,'삼성','삼성 990 PRO 2TB NVMe','2022-10-01'),
 (35,7,'WD','WD Black SN850X 1TB NVMe','2022-08-01'),
 (36,7,'마이크론','마이크론 Crucial T500 2TB NVMe','2024-02-01'),
 (37,8,'프랙탈','프랙탈디자인 Meshify 2 Compact','2021-05-01'),
 (38,8,'NZXT','NZXT H5 Flow','2023-03-01'),
 (39,8,'리안리','리안리 O11 Dynamic','2020-06-01'),
 (40,8,'쿨러마스터','쿨러마스터 NR200P','2021-01-01');

-- ---------------- CPU ----------------
INSERT INTO cpu_specs (part_id, socket, tdp_watt, cores, threads, has_igpu) VALUES
 (1,'AM5',65,6,12,TRUE),(2,'AM5',65,8,16,TRUE),(3,'AM5',120,8,16,TRUE),
 (4,'AM5',170,12,24,TRUE),(5,'AM5',170,16,32,TRUE),
 (6,'LGA1700',125,14,20,TRUE),(7,'LGA1700',125,20,28,TRUE),(8,'LGA1700',125,24,32,TRUE);

-- ---------------- GPU ----------------
INSERT INTO gpu_specs (part_id, vram_gb, length_mm, tdp_watt, recommended_psu_watt) VALUES
 (9,8,240,115,450),(10,16,240,165,550),(11,12,336,220,650),
 (12,16,336,285,700),(13,16,340,320,750),(14,16,330,263,700);

-- ---------------- 메인보드 ----------------
INSERT INTO mainboard_specs (part_id, socket, form_factor, chipset, ram_type, ram_slots, max_ram_gb, m2_slots) VALUES
 (15,'AM5','ATX','B650','DDR5',4,192,3),(16,'AM5','M-ATX','B650','DDR5',4,128,2),
 (17,'AM5','ATX','X670E','DDR5',4,192,4),(18,'LGA1700','M-ATX','B760','DDR5',4,192,2),
 (19,'LGA1700','ATX','B760','DDR5',4,192,3),(20,'LGA1700','ATX','Z790','DDR5',4,192,4);

-- ---------------- RAM ----------------
INSERT INTO ram_specs (part_id, ram_type, capacity_gb, speed_mhz, modules) VALUES
 (21,'DDR5',16,5600,2),(22,'DDR5',32,6000,2),(23,'DDR5',32,6400,2),(24,'DDR5',64,6000,2);

-- ---------------- 파워 ----------------
INSERT INTO psu_specs (part_id, watt, efficiency, form_factor) VALUES
 (25,650,'80+ Gold','ATX'),(26,750,'80+ Gold','ATX'),
 (27,850,'80+ Gold','ATX'),(28,1000,'80+ Platinum','ATX');

-- ---------------- 쿨러 ----------------
INSERT INTO cooler_specs (part_id, type, height_mm, tdp_rating, radiator_mm) VALUES
 (29,'공랭',155,220,NULL),(30,'공랭',160,260,NULL),
 (31,'수랭',NULL,250,240),(32,'수랭',NULL,300,360);

-- ---------------- 저장장치 ----------------
INSERT INTO storage_specs (part_id, `interface`, capacity_gb, form_factor) VALUES
 (33,'NVMe',1000,'M.2'),(34,'NVMe',2000,'M.2'),(35,'NVMe',1000,'M.2'),(36,'NVMe',2000,'M.2');

-- ---------------- 케이스 ----------------
INSERT INTO case_specs (part_id, max_gpu_length_mm, max_cooler_height_mm, max_radiator_mm) VALUES
 (37,360,169,280),(38,365,165,280),(39,420,155,360),(40,330,155,280);

-- ---------------- 쿨러 지원 소켓 (전 쿨러 AM5/LGA1700) ----------------
INSERT INTO cooler_socket_support (part_id, socket) VALUES
 (29,'AM5'),(29,'LGA1700'),(30,'AM5'),(30,'LGA1700'),
 (31,'AM5'),(31,'LGA1700'),(32,'AM5'),(32,'LGA1700');

-- ---------------- 케이스 지원 폼팩터 ----------------
INSERT INTO case_formfactor_support (part_id, form_factor) VALUES
 (37,'ATX'),(37,'M-ATX'),(38,'ATX'),(38,'M-ATX'),
 (39,'ATX'),(39,'M-ATX'),(40,'ITX');

-- 데모 재현성: 출시일을 항상 '최근 6개월'로 (최근 2년 필터 통과 보장)
UPDATE parts SET release_date = DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- 가격조회 정밀 쿼리 오버라이드 예시 (악세서리/변형모델 노이즈 회피)
UPDATE parts SET naver_query = '지포스 RTX 4060 8GB' WHERE part_id = 9;
