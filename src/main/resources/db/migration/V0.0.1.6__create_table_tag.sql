-- IDENTITY data type : https://stackoverflow.com/a/53605454
CREATE TABLE tag(
	id    IDENTITY      PRIMARY KEY,
	name  VARCHAR(100)  NOT NULL UNIQUE
);
