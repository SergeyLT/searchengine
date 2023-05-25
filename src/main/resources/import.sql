set @x := (select count(*) from information_schema.statistics where table_name = 'page' and index_name = 'page_path_si_index' and table_schema = database());
set @sql := if( @x > 0, 'select ''Index exists.''', 'create unique index page_path_si_index on page (path(765), site_id);');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- CREATE TRIGGER after_delete_search_index AFTER DELETE ON search_engine.search_index FOR EACH ROW BEGIN set @v_freq := (select frequency from search_engine.lemma where id = old.lemma_id); if @v_freq > 1 then update search_engine.lemma set frequency = frequency - 1 where id = OLD.lemma_id; else delete from search_engine.lemma where id = OLD.lemma_id; end if; END

DROP PROCEDURE mass_insert_with_deadlock_check
CREATE PROCEDURE mass_insert_with_deadlock_check(  IN sql_query VARCHAR(16000)  )  BEGIN  DECLARE pos INT;  DECLARE count_queries INT;  DECLARE sub_query VARCHAR(1000);  SET count_queries = (CHAR_LENGTH(sql_query) - CHAR_LENGTH(REPLACE(sql_query, ';', ''))+1);  BEGIN  DECLARE EXIT HANDLER FOR SQLEXCEPTION  BEGIN  ROLLBACK;  SIGNAL SQLSTATE '45000'  SET MESSAGE_TEXT = 'Deadlock detected. Transaction rolled back.';  END;  START TRANSACTION;  cycle: WHILE count_queries > 0 DO  SET sub_query = SUBSTRING_INDEX(sql_query,';',1);  IF CHAR_LENGTH(sub_query) < 5 THEN LEAVE cycle; END IF;   BEGIN  SET @query = sub_query;  PREPARE stmt FROM @query;  EXECUTE stmt;  DEALLOCATE PREPARE stmt;  SELECT CONCAT('Seccess insert in ', sub_query) AS message;  END;  SET count_queries = count_queries - 1;  SET sql_query = SUBSTRING(sql_query, LOCATE(';', sql_query)+1);  END WHILE cycle;  COMMIT;  END;  END
