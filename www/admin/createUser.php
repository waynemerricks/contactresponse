<?php

  include('../includes/session.php');
  include('../includes/database.php');

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('../includes/loggedIn.php');
  include('includes/createUser.php');

  if(isAdmin() === FALSE)
    header('Location: ../index.php');//Not an admin so go away
  else if(canCreateUsers() === FALSE)//Admin but can't create users
    header('Location: main.php');//Go back to admin main.php

  $createdUser = FALSE;
  $invalidLoginPassword = FALSE;
  $userExists = FALSE;

  if(isset($_POST['login'], $_POST['password'], $_POST['name'], 
        $_POST['email'])){

    if(isAcceptableLogin($_POST['login']) && isAcceptablePassword(
         $_POST['password']) && isAcceptableEmail($_POST['email'])){

      $mysqli = getDatabaseWrite();

      if(!checkUserNameEmail($mysqli, $_POST['login'], $_POST['email'])){

        $sql = 'INSERT INTO `users` (`login_name`, `password`, `name`, `email`)
                VALUES (?, ?, ?, ?)';

        $stmt = NULL;

        if($stmt = $mysqli->prepare($sql)){
          if($stmt->bind_param('ssss',$_POST['login'], 
               password_hash($_POST['password'],PASSWORD_DEFAULT), $_POST['name'],
               $_POST['email'])){

            if(!$stmt->execute())
              die('Execute Error: ' . $stmt->error);
            else
              $createdUser = TRUE;

          }else
            die('Bind Error: ' . $stmt->error);

        }else
          die('Prepare Error: ' . $mysqli->error);

      }else
        $userExists = TRUE;

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
      <?php if($userExists === TRUE){ ?>
        This user already exists, please use a different email address or login
        name.
      <?php } ?>
    </div>
    <div id="login">
      <form method="post" action="createUser.php">
        <label for="name">Full Name:</label>
        <input id="name" name="name" type="text" value="" /><br>
        <label for="email">Email Address:</label>
        <input id="email" name="email" type="text" value="" /><br>
        <label for="login">User Name:</label>
        <input id="login" name="login" type="text" value="" /><br>
        <label for="password">Password:</label>
        <input id="password" name="password" type="password" value="" /><br>
        <input id="submit" type="submit" value="create" /><br>
      </form>
    </div>
  </body>
</html>
<?php

  $mysqli->close();

?>