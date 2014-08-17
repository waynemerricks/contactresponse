<?php

  session_start();

  /**
   * Destroys the session and unsets $_SESSION vars
   * Then redirects to root/index.php
   */
  function logoutSession(){

    if(isset($_SESSION['user'])
      unset($_SESSION['user']);

    if(isset($_SESSION['userid'])
      unset($_SESSION['userid']);

    if(isset($_SESSION['failedLogins'])
      unset($_SESSION['failedLogins']);

    session_unset();
    session_destroy();
    header("Location: index.php");

  }

?>
