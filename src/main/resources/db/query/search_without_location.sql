SELECT ID object_id,
       NAME, 
       DESCRIPTION   
FROM   OBJECT o 
WHERE  ( :name        is NULL OR  lower(name)        LIKE '%' || lower(:name)        || '%'  )
AND    ( :description is NULL OR  lower(description) LIKE '%' || lower(:description) || '%'  )