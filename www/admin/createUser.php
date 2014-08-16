<?php

  include('../includes/session.php');
  include('includes/createUser.php');

  //DEBUG
  $_SESSION['isAdmin'] = TRUE;
  $_SESSION['canCreateUsers'] = TRUE;

  if(!isset($_SESSION['isAdmin']))
    header('Location: ../index.php');//Not an admin so go away
  else if(!isset($_SESSION['canCreateUsers']))//Admin but can't create users
    header('Location: main.php');//Go back to admin main.php

  include('../includes/database.php');

  $createdUser = FALSE;
  $invalidLoginPassword = FALSE;

  if(isset($_SESSION['canCreateUsers'], $_POST['login'],
       $_POST['password']) && $_SESSION['canCreateUsers']){

    if(isAcceptableLogin($_POST['login']) && isAcceptablePassword(
         $_POST['password'])){

      $mysqli = getDatabaseWrite();

      $sql = 'INSERT INTO `users` (`login_name`, `password`) VALUES (?, ?)';

      $stmt = NULL;

      if($stmt = $mysqli->prepare($sql)){
        if($stmt->bind_param('ss',$_POST['login'], 
             password_hash($_POST['password'],PASSWORD_DEFAULT))){

          if(!$stmt->execute())
            die('Execute Error: ' . $stmt->error);
          else
            $createdUser = TRUE;

        }else
          die('Bind Error: ' . $stmt->error);

      }else
        die('Prepare Error: ' . $mysqli->error);

    }else
      $invalidLoginPassword = TRUE;

  }

?>
<html>
  <head>
    <title>CRS | Admin | Create User</title>
    <link rel="stylesheet" type="text/css" href="../crs.css">
  </head>
  <body>
    <div id="created">
      <?php if($createdUser === TRUE){ ?>
        User Created Succesfully
      <?php } ?>
      <?php if($invalidLoginPassword === TRUE){ ?>
        You have chosen an unsuitable user name or password.<br><br>
        User Names should be at least 8 characters long<br><br>
        Passwords should also be at least 8 characters long. They must contain 
        at least 1 number and 1 upper case letter.<br>
      <?php } ?>
    </div>
    <div id="login">
      <form method="post" action="createUser.php">
        <label for="login">User Name:</label>
        <input id="userName" name="login" type="text" value="" /><br>
        <label for="password">Password:</label>
        <input id="password" name="password" type="password" value="" /><br>
        <input id="submit" type="submit" value="create" /><br>
      </form>
    </div>
  </body>
</html>

