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
        hasPermission('CAN_DELEGATE_ALL') ||
        hasPermission('CAN_DELEGATE_GROUP'))
      $delegate = TRUE;

    return $delegate;

  }


  function canDelegateGroup(){

    $delegate = FALSE;

    if(hasPermission('CAN_DELEGATE_GROUP'))
      $delegate = TRUE;

    return $delegate;

  }

  function canDelegateUnderlings(){

    $delegate = FALSE;

    if(hasPermission('CAN_DELEGATE_UNDERLING'))
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
   * Returns true if this user can view all messages regardless of assigned user
   */
  function canViewAll(){
  
  	$all = FALSE;
  
  	if(hasPermission('VIEW_ALL_INBOX'))
  		$all = TRUE;
  
  	return $all;
  
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
   * Gets the highest group we're a member of for delegation purposes
   */
  function getHighestGroups($mysqli, $userGroups){

    $member = array();
    $sql = 'SELECT `role_id` FROM `role_members` WHERE `user_id` = ' . 
              getLoggedInUserID();
    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc())
      $member[] = $row['role_id'];

    $result->close();
    $rootGroup = NULL;

    foreach($userGroups as $group){

      //$array ($id => $parent)
      foreach($group as $id => $parent){
      	
      	if(in_array($id, $member)){
      	
      		$inParent = TRUE;
      		$rootGroup = $id;
      		
      		while($inParent == TRUE){
      	
      			if(in_array($parent, $member)){
      				$rootGroup = $parent;
      				$parent = $userGroups[$parent];
      			}else
      			  	$inParent = FALSE;
      			
      		}
      	
      	}
      	
      }
      
    }

    return $rootGroup;

  }

  function getUserGroups($mysqli){

    $sql = 'SELECT `id`, `parent` FROM `roles` WHERE `active_permissions` = \'\'';

    $result = $mysqli->query($sql);
    $groups = array();

    while($row = $result->fetch_assoc()){

      $group = array();
      $group[$row['id']] = $row['parent'];
      $groups[] = $group;

    }

    $result->close();

    return $groups;

  }

  function getGroupMembers($group, $mysqli){

    $members = array();

    $sql = 'SELECT `user_id` FROM `role_members` WHERE `role_id` = ' . $group;

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc())
      $members[] = $row['user_id'];

    $result->close();

    return $members;

  }

  function getGroupMembersFromRoot($root, $groups, $mysqli){

    //$groups[id] => parent
    //$root = root group e.g. 6
    $members = array();
    $roles = array();
    $i = -1;

    while($root != NULL){

      foreach($groups as $array){

        foreach($array as $id => $parent){

          if($parent == $root)
            $roles[] = $id;

        }

      }
      //Here we'll have all the roles that had this parent
      //move root to next $role

      $i++;

      if(isset($roles[$i]))
        $root = $roles[$i];
      else
        $root = NULL;

    }

    //Here we'll have all the roles that have members we need
    $sql = 'SELECT `user_id` FROM `role_members` WHERE ';

    if(sizeof($roles) > 0){

      $sql .= '`role_id` = ' . $roles[0];

      for($i = 1; $i < sizeof($roles); $i++)
        $sql .= ' OR `role_id` = ' . $roles[$i];

      $sql .= ' GROUP BY `user_id`';

      $result = $mysqli->query($sql);

      while($row = $result->fetch_assoc())
        $members[] = $row['user_id'];

      $result->close();

    }

    return $members;

  }

  /**
   * Returns a list of users we can delegate to
   */
  function getDelegatesList($mysqli){

    $delegates = array();

    if(canDelegate()){

      $userGroups = getUserGroups($mysqli);
      $rootGroup = getHighestGroups($mysqli, $userGroups);

      if(canDelegateGroup() && !canDelegateUnderlings())//if group but not underlings, get group members
        $members = getGroupMembers($rootGroup, $mysqli);
      else if(!canDelegateGroup() && canDelegateUnderlings())//if not group but underlings get underlings
        $members = getGroupMembersFromRoot($rootGroup, $userGroups, $mysqli);
      else if(canDelegateGroup() && canDelegateUnderlings()){//if both get both and merge

        $rootDelegates = getGroupMembers($rootGroup, $mysqli);
        $childDelegates = getGroupMembersFromRoot($rootGroup, $userGroups, $mysqli);

        $members = array_unique(array_merge($rootDelegates, $childDelegates));

      }

      if(sizeof($members) > 0){

        $first = TRUE;
        $ids = '';

        foreach($members as $user){

          if($first === TRUE){
            $ids .= $user;
            $first = FALSE;
          }else
            $ids .= ',' . $user;
          
        }

        $sql = 'SELECT `id`, `name` FROM `users` WHERE `id` IN (' . $ids .
               ') ORDER BY `name` ASC';

        $result = $mysqli->query($sql);

        while($row = $result->fetch_assoc()){

          $delegate = array();

          if($row['id'] != getLoggedInUserID()){

            $delegate['id'] = $row['id'];
            $delegate['name'] = $row['name'];
            $delegates[] = $delegate;

          }

        }

      }

    }

    return $delegates;

  }

?>