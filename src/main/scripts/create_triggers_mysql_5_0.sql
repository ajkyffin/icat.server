-- Create procedures to update the fileSize of a Dataset or Invesitgation

DELIMITER //

CREATE PROCEDURE UPDATE_DS_FILESIZE (DATASET_ID INTEGER, DELTA BIGINT)
BEGIN
    UPDATE DATASET SET FILESIZE = IFNULL(FILESIZE, 0) + DELTA WHERE ID = DATASET_ID;
END; //

CREATE PROCEDURE UPDATE_INV_FILESIZE (INVESTIGATION_ID INTEGER, DELTA BIGINT)
BEGIN
    UPDATE INVESTIGATION SET FILESIZE = IFNULL(FILESIZE, 0) + DELTA WHERE ID = INVESTIGATION_ID;
END; //

-- Create procedures to update the fileCount of a Dataset or Invesitgation

CREATE PROCEDURE UPDATE_DS_FILECOUNT (DATASET_ID INTEGER, DELTA BIGINT)
BEGIN
    UPDATE DATASET SET FILECOUNT = IFNULL(FILECOUNT, 0) + DELTA WHERE ID = DATASET_ID;
END; //

CREATE PROCEDURE UPDATE_INV_FILECOUNT (INVESTIGATION_ID INTEGER, DELTA BIGINT)
BEGIN
    UPDATE INVESTIGATION SET FILECOUNT = IFNULL(FILECOUNT, 0) + DELTA WHERE ID = INVESTIGATION_ID;
END; //

-- Create triggers to recalculate the fileSize and fileCount after a Datafile insert/update/delete operation

CREATE TRIGGER RECALCULATE_ON_DF_INSERT AFTER INSERT ON DATAFILE
FOR EACH ROW
BEGIN
    SET @DELTA = IFNULL(NEW.FILESIZE, 0);
    CALL UPDATE_DS_FILESIZE(NEW.DATASET_ID, @DELTA);
    CALL UPDATE_DS_FILECOUNT(NEW.DATASET_ID, 1);
    SELECT i.ID INTO @INVESTIGATION_ID FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE ds.ID = NEW.DATASET_ID;
    CALL UPDATE_INV_FILESIZE(@INVESTIGATION_ID, @DELTA);
    CALL UPDATE_INV_FILECOUNT(@INVESTIGATION_ID, 1);
END; //

CREATE TRIGGER RECALCULATE_ON_DF_UPDATE AFTER UPDATE ON DATAFILE
FOR EACH ROW
BEGIN
    IF NEW.DATASET_ID != OLD.DATASET_ID THEN
        SET @DELTA = - IFNULL(OLD.FILESIZE, 0);
        CALL UPDATE_DS_FILESIZE(OLD.DATASET_ID, @DELTA);
        CALL UPDATE_DS_FILECOUNT(OLD.DATASET_ID, -1);
        SELECT i.ID INTO @INVESTIGATION_ID FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE ds.ID = OLD.DATASET_ID;
        CALL UPDATE_INV_FILESIZE(@INVESTIGATION_ID, @DELTA);
        CALL UPDATE_INV_FILECOUNT(@INVESTIGATION_ID, -1);

        SET @DELTA = IFNULL(NEW.FILESIZE, 0);
        CALL UPDATE_DS_FILESIZE(NEW.DATASET_ID, @DELTA);
        CALL UPDATE_DS_FILECOUNT(NEW.DATASET_ID, 1);
        SELECT i.ID INTO @INVESTIGATION_ID FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE ds.ID = NEW.DATASET_ID;
        CALL UPDATE_INV_FILESIZE(@INVESTIGATION_ID, @DELTA);
        CALL UPDATE_INV_FILECOUNT(@INVESTIGATION_ID, 1);

    ELSEIF IFNULL(NEW.FILESIZE, 0) != IFNULL(OLD.FILESIZE, 0) THEN
        SET @DELTA = IFNULL(NEW.FILESIZE, 0) - IFNULL(OLD.FILESIZE, 0);
        CALL UPDATE_DS_FILESIZE(NEW.DATASET_ID, @DELTA);
        SELECT i.ID INTO @INVESTIGATION_ID FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE ds.ID = NEW.DATASET_ID;
        CALL UPDATE_INV_FILESIZE(@INVESTIGATION_ID, @DELTA);
    END IF;
END; //

CREATE TRIGGER RECALCULATE_ON_DF_DELETE AFTER DELETE ON DATAFILE
FOR EACH ROW
BEGIN
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET @INVESTIGATION_ID = NULL;

    SET @DELTA = - IFNULL(OLD.FILESIZE, 0);
    CALL UPDATE_DS_FILESIZE(OLD.DATASET_ID, @DELTA);
    CALL UPDATE_DS_FILECOUNT(OLD.DATASET_ID, -1);
    SELECT i.ID INTO @INVESTIGATION_ID FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE ds.ID = OLD.DATASET_ID;
    CALL UPDATE_INV_FILESIZE(@INVESTIGATION_ID, @DELTA);
    CALL UPDATE_INV_FILECOUNT(@INVESTIGATION_ID, -1);
END; //

-- Create triggers to recalculate the Investigation fileSize and fileCount after a Dataset update/delete operation

CREATE TRIGGER RECALCULATE_ON_DS_UPDATE AFTER UPDATE ON DATASET
FOR EACH ROW
BEGIN
    IF NEW.INVESTIGATION_ID != OLD.INVESTIGATION_ID THEN
        SET @SIZE_DELTA = - IFNULL(OLD.FILESIZE, 0);
        SET @COUNT_DELTA = - IFNULL(OLD.FILECOUNT, 0);
        CALL UPDATE_INV_FILESIZE(OLD.INVESTIGATION_ID, @SIZE_DELTA);
        CALL UPDATE_INV_FILECOUNT(OLD.INVESTIGATION_ID, @COUNT_DELTA);

        SET @SIZE_DELTA = IFNULL(NEW.FILESIZE, 0);
        SET @COUNT_DELTA = IFNULL(NEW.FILECOUNT, 0);
        CALL UPDATE_INV_FILESIZE(NEW.INVESTIGATION_ID, @SIZE_DELTA);
        CALL UPDATE_INV_FILECOUNT(NEW.INVESTIGATION_ID, @COUNT_DELTA);
    END IF;
END; //

CREATE TRIGGER RECALCULATE_ON_DS_DELETE AFTER DELETE ON DATASET
FOR EACH ROW
BEGIN
    SET @SIZE_DELTA = - IFNULL(OLD.FILESIZE, 0);
    SET @COUNT_DELTA = - IFNULL(OLD.FILECOUNT, 0);
    CALL UPDATE_INV_FILESIZE(OLD.INVESTIGATION_ID, @SIZE_DELTA);
    CALL UPDATE_INV_FILECOUNT(OLD.INVESTIGATION_ID, @COUNT_DELTA);
END; //

-- Initialize the fileSizes and fileCounts of all existing Datasets and Investigations

CREATE PROCEDURE INITIALIZE_DS_SIZE_COUNT()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE _id BIGINT UNSIGNED;
    DECLARE cur CURSOR FOR SELECT ID FROM DATASET;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done := TRUE;

    OPEN cur;
    datasetLoop: LOOP
        FETCH cur INTO _id;
        IF done THEN
        LEAVE datasetLoop;
        END IF;

        SELECT SUM(df.FILESIZE) INTO @FILE_SIZE FROM DATASET ds JOIN DATAFILE AS df ON df.DATASET_ID = ds.ID WHERE ds.ID = _id;
        SELECT COUNT(df.ID) INTO @FILE_COUNT FROM DATASET ds JOIN DATAFILE AS df ON df.DATASET_ID = ds.ID WHERE ds.ID = _id;
        UPDATE DATASET SET FILESIZE = IFNULL(@FILE_SIZE, 0) WHERE ID = _id;
        UPDATE DATASET SET FILECOUNT = IFNULL(@FILE_COUNT, 0) WHERE ID = _id;
    END LOOP datasetLoop;
    CLOSE cur;
END; //

CREATE PROCEDURE INITIALIZE_INV_SIZE_COUNT()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE _id BIGINT UNSIGNED;
    DECLARE cur CURSOR FOR SELECT ID FROM INVESTIGATION;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done := TRUE;

    OPEN cur;
    investigationLoop: LOOP
        FETCH cur INTO _id;
        IF done THEN
        LEAVE investigationLoop;
        END IF;

        SELECT SUM(ds.FILESIZE) INTO @FILE_SIZE FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE i.ID = _id;
        SELECT SUM(ds.FILECOUNT) INTO @FILE_COUNT FROM INVESTIGATION i JOIN DATASET AS ds ON ds.INVESTIGATION_ID = i.ID WHERE i.ID = _id;
        UPDATE INVESTIGATION SET FILESIZE = IFNULL(@FILE_SIZE, 0) WHERE ID = _id;
        UPDATE INVESTIGATION SET FILECOUNT = IFNULL(@FILE_COUNT, 0) WHERE ID = _id;
    END LOOP investigationLoop;
    CLOSE cur;
END; //

DELIMITER ;

CALL INITIALIZE_DS_SIZE_COUNT();
CALL INITIALIZE_INV_SIZE_COUNT();

DROP PROCEDURE INITIALIZE_DS_SIZE_COUNT;
DROP PROCEDURE INITIALIZE_INV_SIZE_COUNT;