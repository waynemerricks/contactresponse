<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/user.php'); //User functions
  include('includes/contact.php'); //Contact functions

  if(isset($_POST['type'], $_POST['contact'], $_POST['newest'])){

    $contact = new Contact($_POST['contact'], $mysqli);

    if($contact->hasAssignedHelper() === FALSE)
      $contact->setAssignedUser(getLoggedInUserID());

    $sql = 'UPDATE `messages` SET `status` = \'T\' WHERE `owner` = ? AND `status` = \'D\' AND `id` <= ?';

    $stmt = $mysqli->prepare($sql) or die('MySQL Temp Wait prepare error');
    $stmt->bind_param('ii', $_POST['contact'], $_POST['newest']) or die('MySQL Temp Wait bind error');
    $stmt->execute() or die('MySQL Temp Wait Execute error');

    $stmt->close();

    echo 'SUCCESS';

  }else
    header('Location: index.php');

?>