-- =====================================================================
-- 호환성 검증 시연용 샘플 데이터
-- category_id: 1=CPU 2=GPU 3=MB 4=RAM 5=PSU 6=COOLER 7=STORAGE 8=CASE
-- =====================================================================
SET NAMES utf8mb4;

-- 재실행 대비 초기화
DELETE FROM cooler_socket_support;
DELETE FROM case_formfactor_support;
DELETE FROM cpu_specs; DELETE FROM gpu_specs; DELETE FROM mainboard_specs;
DELETE FROM ram_specs; DELETE FROM psu_specs; DELETE FROM cooler_specs;
DELETE FROM storage_specs; DELETE FROM case_specs;
DELETE FROM parts;

-- 부품 마스터 (part_id 명시)
INSERT INTO parts (part_id, category_id, manufacturer, model_name, release_date) VALUES
 (1, 1,'AMD','라이젠7 7700','2023-01-10'),
 (2, 1,'Intel','코어 i5-14600K','2023-10-17'),
 (3, 2,'NVIDIA','RTX 4070 SUPER','2024-01-17'),
 (4, 2,'NVIDIA','RTX 4060 Ti 16G','2023-07-18'),
 (5, 3,'ASUS','TUF B650-PLUS','2022-09-27'),      -- AM5 / ATX / DDR5
 (6, 3,'MSI','PRO B760M-A','2023-01-05'),          -- LGA1700 / M-ATX / DDR5
 (7, 4,'삼성','DDR5 32G(16x2)','2023-03-01'),
 (8, 4,'삼성','DDR4 16G(8x2)','2021-05-01'),        -- DDR4 (DDR5 보드와 비호환)
 (9, 5,'시소닉','FOCUS 750W Gold','2022-06-01'),
 (10,5,'시소닉','FOCUS 850W Gold','2022-06-01'),
 (11,6,'써멀라이트','PA120 공랭','2021-11-01'),      -- 높이 158
 (12,6,'ARCTIC','LF II 240 수랭','2022-02-01'),
 (13,7,'삼성','990 PRO 1TB NVMe','2022-10-01'),
 (14,8,'프랙탈','미들타워(ATX)','2023-04-01'),        -- ATX/M-ATX, GPU 360, 쿨러 165
 (15,8,'리안리','ITX 케이스','2023-08-01');           -- ITX only, GPU 300, 쿨러 120

INSERT INTO cpu_specs (part_id, socket, tdp_watt, cores, threads, has_igpu) VALUES
 (1,'AM5',65,8,16,TRUE),
 (2,'LGA1700',125,14,20,TRUE);

INSERT INTO gpu_specs (part_id, vram_gb, length_mm, tdp_watt, recommended_psu_watt) VALUES
 (3,12,336,220,650),
 (4,16,240,165,550);

INSERT INTO mainboard_specs (part_id, socket, form_factor, chipset, ram_type, ram_slots, max_ram_gb, m2_slots) VALUES
 (5,'AM5','ATX','B650','DDR5',4,192,3),
 (6,'LGA1700','M-ATX','B760','DDR5',4,192,2);

INSERT INTO ram_specs (part_id, ram_type, capacity_gb, speed_mhz, modules) VALUES
 (7,'DDR5',32,6000,2),
 (8,'DDR4',16,3200,2);

INSERT INTO psu_specs (part_id, watt, efficiency, form_factor) VALUES
 (9,750,'80+ Gold','ATX'),
 (10,850,'80+ Gold','ATX');

INSERT INTO cooler_specs (part_id, type, height_mm, tdp_rating, radiator_mm) VALUES
 (11,'공랭',158,220,NULL),
 (12,'수랭',NULL,250,240);

INSERT INTO storage_specs (part_id, `interface`, capacity_gb, form_factor) VALUES
 (13,'NVMe',1000,'M.2');

INSERT INTO case_specs (part_id, max_gpu_length_mm, max_cooler_height_mm, max_radiator_mm) VALUES
 (14,360,165,280),
 (15,300,120,240);

-- 쿨러 지원 소켓 (둘 다 AM5/LGA1700 지원)
INSERT INTO cooler_socket_support (part_id, socket) VALUES
 (11,'AM5'),(11,'LGA1700'),(12,'AM5'),(12,'LGA1700');

-- 케이스 지원 폼팩터
INSERT INTO case_formfactor_support (part_id, form_factor) VALUES
 (14,'ATX'),(14,'M-ATX'),(15,'ITX');

-- 데모 재현성: 출시일을 항상 '최근 6개월'로 설정
-- (호환성 쿼리의 '최근 2년(release_date)' 필터를 언제 실행해도 통과하도록)
UPDATE parts SET release_date = DATE_SUB(CURDATE(), INTERVAL 6 MONTH);
