<?php

  function getInbox($userid, $canViewAll, $getUnassignedRecords, $mysqli, $lastMessage = NULL){

    $messages = array();
    $sql = 'SELECT `messages`.`created`, `messages`.`type`, `contacts`.`name`,
              `contacts`.`email`, `contacts`.`phone`, `messages`.`preview`, 
              `contacts`.`id`, COUNT(*) AS `waiting`, MAX(`messages`.`id`) AS `msg_id`
            FROM `messages` 
            INNER JOIN `contacts` ON `messages`.`owner` = `contacts`.`id`
            WHERE ';

    if($lastMessage != NULL)
      $sql .= '`messages`.`id` > ' . $lastMessage;

    if($canViewAll === FALSE){

    	$sql .= ' AND (`messages`.`assigned_user` = ' . $userid;
    	
    	if($getUnassignedRecords === TRUE)
    		$sql .= ' OR `messages`.`assigned_user` = 0';
    	
    	$sql .= ') AND ';
    	 
    }
    
    $sql .= '`messages`.`status` = \'D\' AND `messages`.`type` != \'A\'
             GROUP BY `messages`.`owner` ORDER BY `messages`.`created` ASC';

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
      $record['waiting'] = $row['waiting'];
      $record['maxid'] = $row['msg_id'];

      $messages[] = $record;

    }

    $result->close();

    return $messages;

  }

?>