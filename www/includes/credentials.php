<?php

  /**
   * Returns an array with database credentials
   * Change as appropriate
   */
  function getCredentials($readWrite){

    $DB_READ = array();
    $DB_READ['readHostname'] = 'localhost';
    $DB_READ['readUsername'] = 'changeToUserName';
    $DB_READ['readPassword'] = 'changeToPassword';
    $DB_READ['readDatabase'] = 'changeToDatabase';

    //If you have separate WRITE credentials enter them here
    //Otherwise just remark out this section
    $DB_WRITE = array();
    $DB_WRITE['writeHostname'] = 'localhost';
    $DB_WRITE['writeUsername'] = 'changeToUserName';
    $DB_WRITE['writePassword'] = 'changeToPassword';
    $DB_WRITE['writeDatabase'] = 'changeToDatabase';

    $DB = NULL;

    if($readWrite == 'read' || ($readWrite == 'write' && !isset($DB_WRITE))
      $DB = $DB_READ;
    else if($readWrite == 'write' && isset($DB_WRITE))
      $DB = $DB_WRITE;

    return $DB;

  }

?>
