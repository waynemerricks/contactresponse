<?php

  include('includes/session.php');
  logoutSession();
  header("Location: index.php");

?>