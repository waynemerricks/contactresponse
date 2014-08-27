<?php

  include('../includes/session.php'); //Include SESSION stuff
  include('../includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('../includes/loggedIn.php'); //Include logged in check
  include('../includes/user.php'); //User functions
  include('../includes/contact.php'); //Contact functions

  if(isset($_POST['contact'], $_POST['user'])){

    $contact = new Contact($_POST['contact'], $mysqli);
    $contact->setAssignedUser($_POST['user']);

    echo 'SUCCESS';

  }else
    header('Location: ../index.php');

?>