INSERT INTO TAG (name) VALUES ('Lampe');


INSERT INTO USERS(id, username) VALUES ('ab@xy.de','hans-peter');
INSERT INTO PLACE(user ,address,LATITUDE , LONGITUDE) VALUES ('ab@xy.de', 'wurst straße 3', 54.0868,12.1254);
INSERT INTO OBJECT(place,name, DESCRIPTION) VALUES (3,'obj1', 'desc1'); 

INSERT INTO USERS(id, username) VALUES ('cd@xy.de','hans-peter');
INSERT INTO PLACE(user ,address,LATITUDE , LONGITUDE) VALUES ('cd@xy.de', 'zwiebel straße 7', 54.0739 ,12.1450);
INSERT INTO PLACE(user ,address) VALUES ('cd@xy.de', 'apfel straße 2');
INSERT INTO OBJECT(place,name, DESCRIPTION) VALUES (4,'obj2', 'desc2');