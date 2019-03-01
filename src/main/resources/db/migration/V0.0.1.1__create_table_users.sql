/* How must the table look to fit to DbProfileService?
   See example:
   https://github.com/pac4j/pac4j/blob/master/pac4j-sql/src/test/java/org/pac4j/sql/test/tools/DbServer.java
 */
 create
	table
		users(
			id varchar(100) primary key,
			username varchar(100),
			password varchar(300),
			first_nam varchar(100),
			linkedid varchar(100),
			serializedprofile varchar(6000)
		);
