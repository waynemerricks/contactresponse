<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff
  include('includes/login.php'); //Include login functions

  if(!isset($_SESSION['failedLogins']))
    $_SESSION['failedLogins'] = 0;

  $loginError = 0;//Flag for failed to login or not

  if(isset($_POST['login'], $_POST['password'])){

    $mysqli = getDatabaseRead();//Get read connection

    //Verify User Password
    $loginError = checkLogin($mysqli, $_POST['login'], $_POST['password']);

    $mysqli->close();

  }

?>
<html>
  <head>
    <title>CRS | Login</title>
    <link rel="stylesheet" type="text/css" href="css/crs.css">
  </head>
  <body>
    <div id="error">
      <?php if($loginError == 1){ ?>
        Invalid User name or password please try again
      <?php } ?>
    </div>

    <?php if($_SESSION['failedLogins'] < 3){ ?>
      <div id="login">
        <form method="post">
          <label for="login">User Name:</label><input id="login" name="login" type="text" value=""></input><br>
          <label for="password">Password:</label><input id="password" name="password" type="password" value=""></input><br>
          <input id="submit" type="submit" value="login"></input><br>
        </form>
      </div>
    <?php } ?>
  </body>
</html>
