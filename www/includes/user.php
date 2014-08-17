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

?>