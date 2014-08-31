<?php

  /**
   * This page requires a $mysqli variable which is a PHP mysqli::connection
   * Unless the user is not logged in, in which case they get redirected to
   * index.php
   */
  if(!isset($_SESSION['userid']))
    header('Location: index.php');
  else if(!isset($_SESSION['user'])){//Only do this if we don't have permissions already

    //Get User Abilities
    $user = array();

    //Get user name and email
    $sql = 'SELECT `name`, `email` FROM `users` WHERE `id` = ' . 
              $_SESSION['userid'];

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      $user['id'] = $_SESSION['userid'];
      $user['name'] = $row['name'];
      $user['email'] = $row['email'];

    }

    $result->close();

    //Get user permissions
    $sql = 'SELECT GROUP_CONCAT(`roles`.`active_permissions` SEPARATOR \',\') AS
              `permissions`
            FROM `role_members`
            INNER JOIN `roles` ON `role_members`.`role_id` = `roles`.`id`
            WHERE `roles`.`active_permissions` != \'\' AND `role_members`.`user_id` = ' . $_SESSION['userid'];

    $result = $mysqli->query($sql);
    $permissions = NULL;

    while($row = $result->fetch_assoc())
      $permissions = $row['permissions'];

    $result->close();

    if($permissions != NULL){

      $sql = 'SELECT `system_name` FROM permissions WHERE `id` IN (' .
                $permissions . ')';

      $result = $mysqli->query($sql);

      $permissions = array();

      while($row = $result->fetch_assoc())
        $permissions[] = $row['system_name'];

      if(sizeof($permissions) > 0)
        $user['permissions'] = $permissions;

    }

    $_SESSION['user'] = $user;

  }

?>