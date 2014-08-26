<?php

  /**
   * Returns true if this user is the default helper (e.g. where contacts
   * go if not assigned elsewhere)
   */
  function isDefaultHelper(){

    $default = FALSE;

    if(isset($_SESSION['user']['permissions']) && in_array('DEFAULT_HELPER',
          $_SESSION['user']['permissions']))
      $default = TRUE;

    return $default;

  }

  /**
   * Returns true if this user can delegate to other helpers
   */
  function canDelegate(){

    $delegate = FALSE;

    if(isset($_SESSION['user']['permissions']) && (in_array('CAN_DELEGATE_UNDERLING',
          $_SESSION['user']['permissions']) || in_array('CAN_DELEGATE_ALL',
          $_SESSION['user']['permissions'])))
      $delegate = TRUE;

    return $delegate;

  }

  /**
   * Returns true if this user can set contacts as sending junk mail
   */
  function canJunk(){

    $junk = FALSE;

    if(isset($_SESSION['user']['permissions']) && in_array('CAN_JUNK_CONTACT',
          $_SESSION['user']['permissions']))
      $junk = TRUE;

    return $junk;

  }

  /**
   * Returns true if this user can use quick replies from the inbox view
   */
  function canReplyFromInbox(){

    $reply = FALSE;

    if(isset($_SESSION['user']['permissions']) && in_array('CAN_REPLY_INBOX',
          $_SESSION['user']['permissions']))
      $reply = TRUE;

    return $reply;

  }

  /**
   * Returns the id of the logged in user from $_SESSION['user']
   */
  function getLoggedInUserID(){

    $id = NULL;
    if(isset($_SESSION['user']))
      $id = $_SESSION['user']['id'];

    return $id;

  }

  /**
   * Returns a list of users we can delegate to
   */
  function getDelegatesList($mysqli){

    $delegates = array();

    //TODO Restrict based on user thats logged in
    $sql = 'SELECT `id`, `name` FROM `users` ORDER BY `name` ASC';
    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      $delegate = array();

      if($row['id'] != getLoggedInUserID()){

        $delegate['id'] = $row['id'];
        $delegate['name'] = $row['name'];
        $delegates[] = $delegate;

      }

    }

    return $delegates;

  }

?>