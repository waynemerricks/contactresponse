<?php

  function getAutoReplyLabels($mysqli){

    $labels = array();

    $sql = 'SELECT `id`, `label` FROM `templates` ORDER BY `label` ASC';

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      $autoReply = array();
      $autoReply['id'] = $row['id'];
      $autoReply['label'] = $row['label'];
      $labels[] = $autoReply;

    }

    $result->close();

    return $labels;

  }

  function getAutoReplyTypes($mysqli){

    $types = array();

    $sql = 'SELECT `id`, `type` FROM `templates` ORDER BY `label` ASC';

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc())
      $types[$row['type']] = $row['id'];

    $result->close();

    return $types;

  }

?>