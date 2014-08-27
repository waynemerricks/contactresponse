<?php

  include('../includes/session.php'); //Include SESSION stuff
  include('../includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('../includes/loggedIn.php'); //Include logged in check
  include('../includes/user.php'); //User functions

  if(isset($_POST['contact'])){//TODO and session can junk contact

    $sql = 'UPDATE `messages` SET `status` = \'J\', `created_by` = ? WHERE `owner` = ? AND `status` = \'D\'';

    $stmt = $mysqli->prepare($sql) or die('MySQL Junk prepare error');
    $stmt->bind_param('ii', getLoggedInUserID(), $_POST['contact']) or die('MySQL Junk bind error');
    $stmt->execute() or die('MySQL Junk Execute error');

    $stmt->close();

    $sql = 'UPDATE `contacts` SET `status` = \'J\', `updated` = NOW() WHERE `id` = ?';

    $stmt = $mysqli->prepare($sql) or die('MySQL Contact Junk prepare error');
    $stmt->bind_param('i', $_POST['contact']) or die('MySQL Contact Junk bind error');;
    $stmt->execute() or die('MySQL Contact Junk Execute error');

    echo 'SUCCESS';

  }else
    header('Location: ../index.php');

?>