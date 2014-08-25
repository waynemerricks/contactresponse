<?php

  $output = array();
  $returnValue = '';
  $command = '/usr/games/fortune /usr/local/fortune/bible';

  $exec = exec($command, $output, $returnValue);

  foreach($output as $line){

    echo $line . '<br>';

  }

?>