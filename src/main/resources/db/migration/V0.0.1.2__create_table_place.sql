-- DROP TABLE PLACE;

CREATE TABLE place (
	id int auto_increment,
	USER varchar(100) NOT NULL,
	address TEXT,
	latitude DOUBLE,  -- Breitengrad
	longitude DOUBLE, -- Längengrad
	CONSTRAINT place_PK PRIMARY KEY (id),
	CONSTRAINT place_users_FK FOREIGN
    KEY (USER) REFERENCES users(id) ON DELETE CASCADE
) ;

