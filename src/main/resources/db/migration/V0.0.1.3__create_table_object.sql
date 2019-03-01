CREATE TABLE OBJECT (
	id INT auto_increment,
	place integer,
	name TEXT,
	description TEXT,
	lending_infos TEXT,
	image BLOB,
	CONSTRAINT object_PK PRIMARY KEY (id),
	CONSTRAINT object_place_FK FOREIGN KEY (place) REFERENCES place(id) ON DELETE CASCADE
) ;
