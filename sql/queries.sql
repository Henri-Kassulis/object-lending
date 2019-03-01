SELECT * FROM USERS;
SELECT * FROM CONTACT;
SELECT * FROM PLACE;
SELECT * FROM OBJECT;
SELECT * FROM TAG;
SELECT * FROM OBJECT_TAG;
SELECT * FROM object_tag_view;

SELECT array_agg(tag_name)
FROM OBJECT_TAG_VIEW
WHERE object_id= 231;

SELECT GROUP_CONCAT(tag_name separator ',') tags
FROM OBJECT_TAG_VIEW
WHERE object_id= 231;

-- all tables
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE table_schema != 'INFORMATION_SCHEMA';

-- query object that match all given tags
SELECT object_id 
FROM object_tag 
WHERE tag_id IN ( 5,6)
GROUP BY OBJECT_ID
HAVING count(*) = 2


-- distance
SELECT o.ID object_id,o.NAME, o.DESCRIPTION  
FROM (
   SELECT ID 
         , POWER(LATITUDE  - 54.0774, 2) 
         + POWER(LONGITUDE - 12.1720, 2  ) distance
         , "USER"
         ,ADDRESS   
  FROM place 
  
  
-- paging
SELECT * FROM "OBJECT" LIMIT 10 OFFSET 0;


-- ORDER BY distance
SELECT
	o.ID object_id,
	o.NAME,
	o.DESCRIPTION,
	p.LATITUDE,
	p.LONGITUDE FROM OBJECT o
JOIN(
		SELECT
			ID,
			POWER( LATITUDE -2, 2 )+ POWER( LONGITUDE -3, 2 ) distance,
			"USER",
			LATITUDE,
			LONGITUDE
		FROM
			place
	) p ON
	o.PLACE = p.ID
ORDER BY
	nvl(
		distance,
		1
	)
	
	
	-- all objects with joined contact
SELECT o.NAME, o.DESCRIPTION, p.LATITUDE, p.LONGITUDE, c.CONTACT
FROM OBJECT o
JOIN PLACE p
ON o.PLACE = p.ID
JOIN USERS u
ON p."USER" = u.USERNAME
JOIN CONTACT c 
ON c."USER" = u.USERNAME


-- tag names with usage count
SELECT    t.name, 
          count(*) count
FROM      OBJECT_TAG ot 
JOIN      TAG t ON ot.TAG_ID = t.ID
GROUP  BY t.NAME
ORDER  BY count(*) DESC, 
          t.name

