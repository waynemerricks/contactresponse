<?php

  include('../includes/session.php');

  //DEBUG 
  $_SESSION['isAdmin'] = TRUE;
  $_SESSION['canCreateUsers'] = TRUE;
  
  if(!isset($_SESSION['isAdmin']))
    header('Location: ../index.php');//Not an admin so go away
  else if(!isset($_SESSION['canCreateUsers']))//Admin but can't create users
    header('Location: main.php');//Go back to admin main.php

  include('../includes/database.php');

  $createdUser = FALSE;

  if(isset($_SESSION['canCreateUsers'], $_POST['userName'], $_POST['email'],
       $_POST['password']) && $_SESSION['canCreateUsers']){

    $mysqli = getDatabaseWrite();

    $sql = 'INSERT INTO `users` (`login_name`, `password`) VALUES (?, ?);

    if($stmt = $mysqli->prepare($sql) && $stmt->bind_param('ss',
         $_POST['username'], password_hash($_POST['password'],
         PASSWORD_DEFAULT)) && $stmt->execute())
      $createdUser = TRUE;

  }

?>
<html>
  <head>
    <title>CRS | Admin | Create User</title>
    <link rel="stylesheet" type="text/css" href="../crs.css">
  </head>
  <body>
    <div id="created">
      <?php if($createdUser){ ?>
        User Created Succesfully
      <?php } ?>
    </div>
    <div id="login">
      <form method="post">
        <label for="login">User Name:</label><input id="login" name="login" type="text" value=""></input><br>
        <label for="password">Password:</label><input id="password" name="password" type="password" value=""></input><br>
        <input id="submit" type="submit" value="login"></input><br>
      </form>
    </div>
  </body>
</html>

