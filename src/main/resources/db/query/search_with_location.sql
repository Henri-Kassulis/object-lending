SELECT o.ID object_id,
       o.NAME,
       o.DESCRIPTION, 
       p.LATITUDE,
       p.LONGITUDE 
FROM   OBJECT o  
JOIN   (    SELECT ID
           , POWER(LATITUDE  - :latitude, 2)
           + POWER(LONGITUDE - :longitude, 2  ) distance          
           , "USER"          
           ,LATITUDE             
           ,LONGITUDE              
           FROM place  
       ) p 
ON     o.PLACE = p.ID  
WHERE  ( :name        is NULL OR  lower(name)        LIKE '%' || lower(:name)        || '%'  )
AND    ( :description is NULL OR  lower(description) LIKE '%' || lower(:description) || '%'  )
ORDER  BY distance