<?php

  /**
   * Helper method to test for the given permission
   */
  function hasPermission($permission){

    $hasIt = FALSE;

    if(isset($_SESSION['user']['permissions']) && in_array($permission,
          $_SESSION['user']['permissions']))
      $hasIt = TRUE;

    return $hasIt;

  }

  /** Returns users name (not the login name) */
  function getUsersName(){

    return $_SESSION['user']['name'];

  }

  /** True if user can create other users */
  function canCreateUsers(){

    $create = FALSE;

    if(hasPermission('CREATE_USERS'))
      $create = TRUE;

    return $create;

  }

  /** True if user can admin permissions */
  function canEditPermissions(){

    $perms = FALSE;

    if(hasPermission('EDIT_PERMISSIONS'))
      $perms = TRUE;

    return $perms;

  }

  /** True if user can admin roles */
  function canEditRoles(){

    $roles = FALSE;

    if(hasPermission('EDIT_ROLES'))
      $roles = TRUE;

    return $roles;

  }

  /**
   * Returns true if this user is the default helper (e.g. where contacts
   * go if not assigned elsewhere)
   */
  function isDefaultHelper(){

    $default = FALSE;

    if(hasPermission('DEFAULT_HELPER'))
      $default = TRUE;

    return $default;

  }

  /**
   * Returns true if this user can delegate to other helpers
   */
  function canDelegate(){

    $delegate = FALSE;

    if(hasPermission('CAN_DELEGATE_UNDERLING') ||
        hasPermission('CAN_DELEGATE_ALL'))
      $delegate = TRUE;

    return $delegate;

  }

  /**
   * Returns true if this user can set contacts as sending junk mail
   */
  function canJunk(){

    $junk = FALSE;

    if(hasPermission('CAN_JUNK_CONTACT'))
      $junk = TRUE;

    return $junk;

  }

  /**
   * Returns true if this user can view admin menu
   */
  function isAdmin(){

    $admin = FALSE;

    if(hasPermission('USER_ADMIN') || hasPermission('SYS_ADMIN'))
      $admin = TRUE;

    return $admin;

  }

  /**
   * Returns true if this user can use quick replies from the inbox view
   */
  function canReplyFromInbox(){

    $reply = FALSE;

    if(hasPermission('CAN_REPLY_INBOX'))
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