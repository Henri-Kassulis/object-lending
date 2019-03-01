-- IDENTITY data type : https://stackoverflow.com/a/53605454
CREATE TABLE object_tag(
	object_id INT      NOT NULL,
	tag_id    BIGINT   NOT NULL,
	CONSTRAINT object_tag_uk UNIQUE KEY (object_id,tag_id),
	CONSTRAINT object_tag_object_fk 
	  FOREIGN KEY (object_id) 
	  REFERENCES object(id) 
	  ON DELETE CASCADE,
	CONSTRAINT object_tag_tag_fk 
	  FOREIGN KEY (tag_id) 
	  REFERENCES tag(id) 
	  ON DELETE CASCADE
);