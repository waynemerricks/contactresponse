<?php

  include('includes/sessions.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff
  include('includes/login.php'); //Include login functions

  $loginError = FALSE;//Flag for failed to login or not

  if(isset($_POST['login'], $_POST['password'])){

    $mysqli = getDatabaseRead();//Get read connection

    //Verify User Password
    checkLogin($mysqli, $_POST['login'], $_POST['password']);

  }

?>
<html>
  <head>
    <title>CRS | Login</title>
  </head>
  <body>
    <div id="error">
      <?php if($loginError){ ?>
        Invalid User name or password please try again
      <?php } ?>
    </div>

    <?php if($_SESSION['failedLogins'] < 3){ ?>
      <div id="login">
        <form method="post">
          <input id="login" name="login" type="text" value=""></input>
          <input id="password" name="password" type="password" value=""></input>
          <input id="submit" type="submit" value="login"></input>
        </form>
      </div>
    <?php } ?>
  </body>
</html>
