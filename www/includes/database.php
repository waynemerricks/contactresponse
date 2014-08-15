<?php

  include('credentials.php');//DB User/Pass details

  /**
   * Returns a MySQLi Read connection or dies with an error
   */
  function getDatabaseRead(){

    $DB = getCredentials('read');

    $mysqli = new $mysqli($DB['hostname'], $DB['username'], $DB['password'],
                       $DB['database']);

    if($mysqli->connect_errno)
      die('Failed to connect to MySQL: (' . $mysqli->connect_errno . ') '
            . $mysqli->connect_error;

    return $mysqli;

  }

  /**
   * Returns a MySQLi Write connection or dies with an error
   */
  function getDatabaseWrite(){

    $DB = getCredentials('write');

    if($mysqli->connect_errno)
      die('Failed to connect to MySQL: (' . $mysqli->connect_errno . ') '
            . $mysqli->connect_error;

    return $mysqli;

  }

?>
