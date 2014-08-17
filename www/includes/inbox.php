<?php

  function getInbox($userid, $getUnassignedRecords, $mysqli){

    $messages = array();
    $sql = 'SELECT `messages`.`created`, `messages`.`type`, `contacts`.`name`,
              `contacts`.`email`, `contacts`.`phone`, `messages`.`preview`, 
              `contacts`.`id`
            FROM `messages` 
            INNER JOIN `contacts` ON `messages`.`owner` = `contacts`.`id`
            WHERE (`messages`.`assigned_user` = ' . $userid;

    if($getUnassignedRecords === TRUE)
      $sql .= ' OR `messages`.`assigned_user` = 0';

    $sql .= ') AND `messages`.`status` = \'D\' GROUP BY `messages`.`owner`';

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      //date, name, preview
      $record = array();

      $today = date('Ymd');
      $recordDay = date('Ymd', strtotime($row['created']));
      $format = 'd/m/Y';

      if($today == $recordDay)
        $format = 'H:i';

      $record['date'] = date($format, strtotime($row['created']));
      $record['type'] = $row['type'];
      $record['name'] = trim($row['name']);

      if(strlen($record['name']) == 0 ||
          strtolower($record['name']) == 'unknown'){

        //Only Show last 4 digits of number if phone or SMS
        if(($row['type'] == 'P' || $row['type'] == 'S') && 
            strlen($row['phone']) > 4)
          $record['name'] = 'Ends with: ' . substr($row['phone'], -4);

      }

      $record['preview'] = $row['preview'];
      $record['id'] = $row['id'];

      $messages[] = $record;

    }

    $result->close();

    return $messages;

  }

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

?>