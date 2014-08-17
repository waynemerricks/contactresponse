<?php

  /**
   * Gets the messages that need to be dealt with from the given contact
   * @param $user
   * @param $contact
   * @param $getUnassignedRecords
   * @param $mysqli
   * @return
   */
  function getPendingMessages($user, $contact, $getUnassignedRecords, $mysqli){

    $messages = array();
    $sql = 'SELECT `messages`.`id`, `messages`.`created`, `messages`.`type`
            FROM `messages`
            WHERE (`messages`.`assigned_user` = ' . $user;

    if($getUnassignedRecords === TRUE)
      $sql .= ' OR `messages`.`assigned_user` = 0';

    $sql .= ') AND `messages`.`status` = \'D\' AND `owner` = ' . $contact;

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      //date, type, data from file
      $record = array();

      $format = 'd/m/Y H:i:s';

      $record['date'] = date($format, strtotime($row['created']));
      $record['type'] = $row['type'];
      $record['id'] = $row['id'];

      $message = getMessage($row['id'], $record['type']);
      $record['message'] = $message['body'];
      $record['subject'] = '';

      if(isset($message['subject']))
        $record['subject'] = $message['subject'];

      $messages[] = $record;

    }

    $result->close();

    return $messages;

  }

  /**
   * Gets the message content from the file archive
   * @param $id ID of message to retrieve
   * @param $type Message type
   * @return string containing plain text message
   */
  function getMessage($id, $type){

    $message = array();

    if(is_file('archive/' . $id)){

      $message['body'] = file_get_contents('archive/' . $id);

      if($type == 'E'){//Email so get subject

        $message['subject'] = getSubject($message['body']);
        $message['body'] = removeFirstLine($message['body']);

      }

    }

    return $message;

  }

  function getSubject($string){

    $pos = strpos($string, "\n");

    if($pos !== FALSE)
      $string = substr($string, 0, $pos);

    if(substr($string, 0, 2) === 'S:')
      $string = substr($string, 2);

    return $string;

  }


  function removeFirstLine($string){

    $pos = strpos($string, "\n");

    if($pos !== FALSE)
      $string = substr($string, $pos + 1);

    return $string;

  }

?>