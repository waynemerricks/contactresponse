<?php

  /**
   * Checks if user can login and updates failed counts as appropriate
   * If we can login, user is redirected to main.php
   * @param $mysqli MySQLi connection
   * @param $username User Name of person to check
   * @param $password clear text password they typed in
   */
  function checkLogin($mysqli, $username, $password){

    $mysqli = getDatabaseRead();//Get read connection

    //Verify User Password
    $sql = 'SELECT `password`, `id` FROM `users` WHERE `login_name` = ?';

    if($stmt = $mysqli->prepare($sql)){

      if($stmt->bind_param('s', $username)){

        if($stmt->execute()){

          $stmt->bind_result($hash, $id);

          while($stmt->fetch()){

            if(password_verify($password, $hash)){

              $_SESSION['userid'] = $id; //Set logged in user ID
              header('Location: main.php');

            }else{

              incrementLoginFail();
              $loginError = TRUE;

            }

          }

          $stmt->close();

        }else
          die('Failed to execute login lookup' . $stmt->error);

      }else
        die('Failed to bind login lookup: ' . $stmt->error);

    }else
      die('Failed to prepare login lookup:' . $mysqli->error);

  }

  /**
   * Increments the SESSION['failedLogins'] variable
   */
  function incrementLoginFail(){

    if(isset($_SESSION['failedLogins']))
      $_SESSION['failedLogins']++;
    else
      $_SESSION['failedLogins'] = 1;

  }

?>
