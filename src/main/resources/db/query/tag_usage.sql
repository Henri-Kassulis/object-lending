-- tag names with usage count
SELECT    t.name, 
          count(*) count
FROM      OBJECT_TAG ot 
JOIN      TAG t ON ot.TAG_ID = t.ID
GROUP  BY t.NAME
ORDER  BY count(*) DESC, 
          lower(t.name)