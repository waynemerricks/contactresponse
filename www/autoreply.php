<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/user.php'); //User functions

  if(isset($_POST['template'], $_POST['contact'], $_POST['newest'])){

    $sql = 'UPDATE `messages` SET `status` = \'A\' WHERE `owner` = ? AND `status` = \'D\' AND `id` < ?';

    $stmt = $mysqli->prepare($sql) or die('MySQL Archive prepare error');
    $stmt->bind_param('ii', $_POST['contact'], $_POST['newest']) or die('MySQL Archive bind error');
    $stmt->execute() or die('MySQL Archive Execute error');

    $stmt->close();

    $sql = 'UPDATE `messages` SET `status` = \'R\' WHERE `id` = ?';

    $stmt = $mysqli->prepare($sql) or die('MySQL Reply prepare error');
    $stmt->bind_param('i', $_POST['newest']) or die('MySQL Reply bind error');;
    $stmt->execute() or die('MySQL Reply Execute error');

    $sql = 'INSERT INTO `messages` (`owner`, `type`, `direction`, `created_by`, `status`) VALUES (?, ?, ?, ?, ?)';
    $user = getLoggedInUserID();
    $stmt = $mysqli->prepare($sql) or die('MySQL Outbound prepare error');
    $stmt->bind_param('issis', $_POST['contact'], $_POST['template'], $out = 'O', getLoggedInUserID(), $unknown = 'U') or die('MySQL Outbound bind error');
    $stmt->execute() or die('MySQL Outbound Execute error');

    echo 'SUCCESS';

  }else
    header('Location: index.php');

?>