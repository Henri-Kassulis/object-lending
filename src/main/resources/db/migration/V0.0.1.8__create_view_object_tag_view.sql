CREATE VIEW object_tag_view AS 
SELECT ot.OBJECT_ID, 
       ot.TAG_ID, 
       t.NAME AS tag_name
FROM   OBJECT_TAG ot 
JOIN   TAG t ON ot.TAG_ID = t.ID