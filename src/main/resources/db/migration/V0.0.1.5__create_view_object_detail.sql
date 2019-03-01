CREATE VIEW object_detail AS 
SELECT o.id, o.name, o.description, p.latitude, p.longitude, c.contact
  FROM object o
  JOIN place p
    ON o.place = p.id
  JOIN users u
    ON p.user = u.username
  JOIN contact c 
    ON c.user = u.username