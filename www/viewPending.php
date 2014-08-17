<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/messages.php'); //Functions for getting messages
  include('includes/contact.php'); //Functions for getting a contact
  $pendingMessages = array();

  if(isset($_GET['id'])){

    $pendingMessages = getPendingMessages(getUserId(), $_GET['id'], $mysqli);
    $contact = getContact($_GET['id']);

  }else
    header('Location: main.php');//We got here weirdly so just go back to main

  //DEBUG VALUES
  $contact['name'] = 'Name Test';
  $contact['location'] = 'England';
  $contact['gender'] = 'Male';
  $contact['notes'] = 'Testing value remove after debugging';

?>
<html>
  <head>
    <title>CRS | <?php echo $contact['name']; ?></title>
    <link rel="stylesheet" type="text/css" href="crs.css">
  </head>
  <body>
    <div class="contact">
      <table class="contact">
       <tr>
         <td rowspan="4"><img src="images/nophoto.png" /></td>
         <th>Name:</th>
         <td><?php echo $contact['name']; ?></td>
       </tr>
       <tr>
         <th>Location:</th>
         <td><?php echo $contact['location']; ?></td>
       </tr>
       <tr>
         <th>Gender:</th>
         <td><?php echo $contact['gender']; ?></td>
       </tr>
       <tr>
         <th>Notes:</th>
         <td><?php echo $contact['notes']; ?></td>
       </tr>
      </table>
    </div>
    <div class="pending">
      <table class="pending">
        <tr class="header">
          <th>&nbsp;</th>
          <th class="centre">Date</th>
          <th class="spacer">&nbsp;</th>
        </tr>
        <?php if(sizeof($pendingMessages) == 0){ ?>
          <tr><td colspan="3">There are no messages for this contact</td></tr>
        <?php
          } else {

            for($i = 0; $i < sizeof($pendingMessages); $i++){ 

              $src = 'images/generic.png';

              if($pendingMessages[$i]['type'] == 'E')
                $src = 'images/email.png';
              else if($pendingMessages[$i]['type'] == 'P')
                $src = 'images/phone.png';
              else if($pendingMessages[$i]['type'] == 'S')
                $src = 'images/sms.png';
        ?>
          <tr>
            <td class="icon">
              <img class="icon" src="<?php echo $src; ?>" />
            </td>
            <td colspan="2" class="time centre">
              <?php echo $pendingMessages[$i]['date']; ?>
            </td>
          </tr>
          <tr class="message">
            <td colspan="3" class="message">
              <?php echo $pendingMessages[$i]['message']; ?>
            </td>
          </tr>
        <?php
            }
          }
        ?>
      </table>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>