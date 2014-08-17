<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/inbox.php'); //Functions for getting the inbox of this user

  $contactsPending = getInbox(getUserId());

?>
<html>
  <head>
    <title>CRS | Login</title>
    <link rel="stylesheet" type="text/css" href="crs.css">
  </head>
  <body>
    <div class="inbox">
      <table class="inbox">
        <tr>
          <th>Contact</th>
          <th>Messages</th>
          <th>Date</th>
        </tr>
        <?php for($i = 0; $i < sizeof($contactsPending); $i++){ ?>
          <tr>
            <td class="name"><?php echo $contactsPending[$i]['name']; ?></td>
            <td><?php echo $contactsPending[$i]['numberOfMessages']; ?></td>
            <td class="time"><?php echo $contactsPending[$i]['date']; ?></td>
          </tr>
          <tr class="preview">
            <td class="preview"><?php echo $contactsPending[$i]['preview']; ?></td>
          </tr>
        <?php } ?>
      </table>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>