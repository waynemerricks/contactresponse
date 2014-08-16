<?php

  /**
   * Checks to see if the login name is at least 8 characters
   */
  function isAcceptableLogin($name){

    $acceptable = FALSE;

    if(strlen($name) > 7)
      $acceptable = TRUE;

    return $acceptable;

  }

  /**
   * Checks to see if the password is at least 8 characters
   * and contains at least one number and one upper/lower case
   * character
   */
  function isAcceptablePassword($password){

    $acceptable = FALSE;

    if(strlen($password) > 7 && preg_match('/[A-Za-z]/', $password) &&
          preg_match('/[0-9]/', $password))
      $acceptable = TRUE;

    return $acceptable;

  }
  
  /**
   * Checks to see if the email is valid
   */
  function isAcceptableEmail($email){

    $acceptable = FALSE;

    if(filter_var($email, FILTER_VALIDATE_EMAIL) !== FALSE)
      $acceptable = TRUE;

    return $acceptable;

  }

  function checkUserNameEmail($login, $email){
    //TODO
    return TRUE;

  }

?>