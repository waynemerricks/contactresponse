<?php

  /**
   * Returns an array with database credentials
   * Change as appropriate
   */
  function getCredentials($readWrite){

    $DB_READ = array();
    $DB_READ['hostname'] = 'localhost';
    $DB_READ['username'] = 'changeToUserName';
    $DB_READ['password'] = 'changeToPassword';
    $DB_READ['database'] = 'changeToDatabase';

    //If you have separate WRITE credentials enter them here
    //Otherwise just remark out this section
    $DB_WRITE = array();
    $DB_WRITE['hostname'] = 'localhost';
    $DB_WRITE['username'] = 'changeToUserName';
    $DB_WRITE['password'] = 'changeToPassword';
    $DB_WRITE['database'] = 'changeToDatabase';

    $DB = NULL;

    if($readWrite == 'read' || ($readWrite == 'write' && !isset($DB_WRITE)))
      $DB = $DB_READ;
    else if($readWrite == 'write' && isset($DB_WRITE))
      $DB = $DB_WRITE;

    return $DB;

  }

?>
