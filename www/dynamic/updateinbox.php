<?php

  if(isset($_GET['lastMessage'])){

    include('../includes/session.php'); //Include SESSION stuff
    include('../includes/database.php'); //Include DB stuff

    $mysqli = getDatabaseRead(); //Get MySQL connection for page

    include('../includes/loggedIn.php'); //Include logged in check
    include('../includes/inbox.php'); //Functions for getting the inbox of this user
    include('../includes/user.php'); //Functions relating to logged in user

    $contactsPending = getInbox(getUserId(), isDefaultHelper(), $mysqli, $_GET['lastMessage']);

    if(sizeof($contactsPending) > 0)
      echo 'SUCCESS|' . sizeof($contactsPending);
    else
      echo 'SUCCESS|0';

  }else
    echo 'FAIL';

?>