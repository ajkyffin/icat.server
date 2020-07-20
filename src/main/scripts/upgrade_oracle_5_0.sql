-- Add size and fileCount columns to Dataset and Investigation tables

ALTER TABLE DATASET ADD DATASETSIZE NUMBER(19);
ALTER TABLE DATASET ADD FILECOUNT NUMBER(19);
ALTER TABLE INVESTIGATION ADD INVESTIGATIONSIZE NUMBER(19);
ALTER TABLE INVESTIGATION ADD FILECOUNT NUMBER(19);

-- Initialize the sizes and fileCounts of all existing Datasets and Investigations

CREATE PROCEDURE INITIALIZE_DATASET_SIZES_FILECOUNTS AS
DATASET_SIZE NUMBER(19);
FILE_COUNT NUMBER(19);
CURSOR CUR IS SELECT ID FROM DATASET;
BEGIN
    FOR CUR_DATASET in CUR LOOP
        SELECT SUM(df.FILESIZE) INTO DATASET_SIZE FROM DATASET ds JOIN DATAFILE df ON df.DATASET_ID = ds.ID WHERE ds.ID = CUR_DATASET.ID;
        SELECT COUNT(df.ID) INTO FILE_COUNT FROM DATASET ds JOIN DATAFILE df ON df.DATASET_ID = ds.ID WHERE ds.ID = CUR_DATASET.ID;
        UPDATE DATASET SET DATASETSIZE = DATASET_SIZE WHERE ID = CUR_DATASET.ID;
        UPDATE DATASET SET FILECOUNT = FILE_COUNT WHERE ID = CUR_DATASET.ID;
    END LOOP;
END;
/

CREATE PROCEDURE INITIALIZE_INVESTIGATION_SIZES_FILECOUNTS AS
INVESTIGATION_SIZE NUMBER(19);
FILE_COUNT NUMBER(19);
CURSOR CUR IS SELECT ID FROM INVESTIGATION;
BEGIN
    FOR CUR_INVESTIGATION in CUR LOOP
        SELECT SUM(ds.DATASETSIZE) INTO INVESTIGATION_SIZE FROM INVESTIGATION i JOIN DATASET ds ON ds.INVESTIGATION_ID = i.ID WHERE i.ID = CUR_INVESTIGATION.ID;
        SELECT SUM(ds.FILECOUNT) INTO FILE_COUNT FROM INVESTIGATION i JOIN DATASET ds ON ds.INVESTIGATION_ID = i.ID WHERE i.ID = CUR_INVESTIGATION.ID;
        UPDATE INVESTIGATION SET INVESTIGATIONSIZE = INVESTIGATION_SIZE WHERE ID = CUR_INVESTIGATION.ID;
        UPDATE INVESTIGATION SET FILECOUNT = FILE_COUNT WHERE ID = CUR_INVESTIGATION.ID;
    END LOOP;
END;
/

BEGIN
    INITIALIZE_DATASET_SIZES_FILECOUNTS;
    INITIALIZE_INVESTIGATION_SIZES_FILECOUNTS;
END;
/

DROP PROCEDURE INITIALIZE_DATASET_SIZES_FILECOUNTS;
DROP PROCEDURE INITIALIZE_INVESTIGATION_SIZES_FILECOUNTS;

exit