ALTER TABLE PERSON MODIFY COLUMN PASSWORD VARCHAR(256) NOT NULL

UPDATE PERSON SET PASSWORD = CONCAT('SALT_', SALT, PASSWORD)

ALTER TABLE PERSON DROP COLUMN SALT

CREATE TABLE PERSON_PASSWORD
	(PERSON_ID INTEGER NOT NULL,
	PASSWORD VARCHAR(256) NOT NULL,
	PASSWORD_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT PERSON_ID_PP_FK FOREIGN KEY(PERSON_ID) REFERENCES PERSON(ID) ON DELETE CASCADE) ENGINE=InnoDB

INSERT INTO PERSON_PASSWORD (PERSON_ID, PASSWORD) SELECT ID, PASSWORD FROM PERSON

ALTER TABLE PERSON DROP COLUMN PASSWORD

ALTER TABLE PERSON ADD COLUMN GRACE_PERIOD_START TIMESTAMP NULL DEFAULT NULL

INSERT INTO CONFIGURATION (CATEGORY, NAME, VALUE) VALUES ('core', 'smtp.timeout', '5000')

INSERT INTO CONFIGURATION (CATEGORY, NAME, VALUE) VALUES ('core', 'encryption.key', (SELECT DATA FROM ENCRYPTION_KEY))

DROP TABLE ENCRYPTION_KEY