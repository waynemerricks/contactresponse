<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/loggedIn.php'); //Include logged in check
  include('includes/database.php'); //Include DB stuff

?>
<html>
  <head>
    <title>CRS | Login</title>
    <link rel="stylesheet" type="text/css" href="crs.css">
  </head>
  <body>
    Place Holder Logged in successfully: User ID <?php echo $_SESSION['userid']; ?>
  </body>
</html>