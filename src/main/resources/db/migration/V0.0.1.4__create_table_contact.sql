-- DROP TABLE PLACE;

CREATE TABLE contact (
	USER varchar(100) NOT NULL,
	contact TEXT,
	CONSTRAINT contact_users_FK FOREIGN
    KEY (USER) REFERENCES users(id) ON DELETE CASCADE
) ;


