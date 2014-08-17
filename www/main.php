<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check


?>
<html>
  <head>
    <title>CRS | Login</title>
    <link rel="stylesheet" type="text/css" href="crs.css">
  </head>
  <body>
    Place Holder Logged in successfully: User ID <?php echo $_SESSION['userid']; ?><br>
    <?php print_r($_SESSION); ?>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>